package com.notare.feature.voicenote.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.StorageException;
import com.notare.common.exception.VoiceNoteNotFoundException;
import com.notare.common.storage.FileStorageService;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.ClipChange;
import com.notare.feature.voicenote.mapper.VoiceNoteMapper;
import com.notare.feature.voicenote.model.VoiceNote;
import com.notare.feature.voicenote.repository.VoiceNoteRepository;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.dto.response.VoiceUpload;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voicenote.validation.VoiceUploadValidator;
import lombok.RequiredArgsConstructor;
import com.notare.feature.voicenote.event.VoiceNoteCreatedEvent;
import com.notare.feature.voicenote.event.VoiceNoteDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoiceNoteServiceImpl implements VoiceNoteService {

    private final VoiceNoteRepository voiceNoteRepository;
    private final VoiceNoteMapper voiceNoteMapper;
    private final VoiceUploadValidator voiceUploadValidator;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Cross-module, PUBLIC API. Used ONLY as an ownership gate — confirming
     * the caller owns the NOTE, not confirming anything about individual
     * voice notes — in {@link #listVoiceNotesForNote} and (as a fallback,
     * after direct ownership fails) in {@link #loadAudio}. See those
     * methods' own doc comments for why each needed it.
     */
    private final NoteService noteService;

    @Override
    public VoiceNoteResponse upload(Long ownerId, MultipartFile file, Long noteId, Integer durationSeconds) {
        return store(ownerId, noteId, null, file, durationSeconds).clip();
    }

    @Override
    public VoiceUpload uploadForNote(Long uploaderId, Long noteId, MultipartFile file, String clientId,
                                     Integer durationSeconds) {
        return store(uploaderId, noteId, clientId, file, durationSeconds);
    }

    /** The one upload path. A client id makes it idempotent; without one it always stores. */
    private VoiceUpload store(Long ownerId, Long noteId, String clientId, MultipartFile file, Integer durationSeconds) {
        // 0. A repeat of a clip already stored answers with that clip, before any bytes are written.
        if (clientId != null) {
            Optional<VoiceNote> existing = voiceNoteRepository.findByOwnerIdAndClientId(ownerId, clientId);
            if (existing.isPresent()) {
                return new VoiceUpload(voiceNoteMapper.toResponse(existing.get()), false);
            }
        }

        // 1. Validate before writing anything anywhere.
        voiceUploadValidator.validate(file, durationSeconds);

        // 2. Store the bytes OUTSIDE any DB transaction. We must not hold a
        //    database connection open across (potentially slow) file I/O, and
        //    the filesystem/S3 is not part of a DB transaction anyway. This is
        //    why upload() is deliberately NOT annotated @Transactional.
        String storageKey = storeBytes(file);

        // 3. Persist the metadata row. The file already exists on disk now, so
        //    if this save fails the file would be orphaned — a row-less file.
        //    We compensate: delete the stored bytes, then rethrow. This is the
        //    saga / compensating-transaction idea in miniature, needed because
        //    a DB transaction cannot roll back a filesystem write.
        try {
            VoiceNote entity = voiceNoteMapper.toEntity(
                    ownerId, noteId,
                    file.getOriginalFilename(), file.getContentType(),
                    storageKey, file.getSize(), durationSeconds);
            entity.setClientId(clientId);
            VoiceNote saved = voiceNoteRepository.save(entity);

            // Announce the creation only after the metadata row exists.
            // Consumers such as the sharing module can now grant access to
            // this recording when its parent note is already shared.
            eventPublisher.publishEvent(new VoiceNoteCreatedEvent(
                    saved.getId(),
                    saved.getOwnerId(),
                    saved.getNoteId()));

            return new VoiceUpload(voiceNoteMapper.toResponse(saved), true);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a replay of the same upload: uq_voice_notes_owner_client_id fired. The
            // other attempt stored the clip, so this attempt's bytes are surplus and its clip is the answer.
            fileStorageService.delete(storageKey);
            if (clientId == null) {
                throw e;
            }
            return voiceNoteRepository.findByOwnerIdAndClientId(ownerId, clientId)
                    .map(clip -> new VoiceUpload(voiceNoteMapper.toResponse(clip), false))
                    .orElseThrow(() -> e);
        } catch (RuntimeException e) {
            fileStorageService.delete(storageKey);
            throw e;
        }
    }

    @Override
    public VoiceUpload copyClip(Long userId, Long sourceVoiceNoteId, Long targetNoteId, String clientId) {
        Optional<VoiceNote> existing = voiceNoteRepository.findByOwnerIdAndClientId(userId, clientId);
        if (existing.isPresent()) {
            return new VoiceUpload(voiceNoteMapper.toResponse(existing.get()), false);
        }

        // A clip its owner removed on its own is gone for everyone, same as a deleted one.
        VoiceNote source = voiceNoteRepository.findById(sourceVoiceNoteId)
                .filter(clip -> !clip.isRemoved())
                .orElseThrow(() -> new VoiceNoteNotFoundException("Voice note not found."));

        String storageKey = fileStorageService.copy(source.getStorageKey(), source.getOriginalFilename());
        try {
            VoiceNote entity = voiceNoteMapper.toEntity(
                    userId, targetNoteId,
                    source.getOriginalFilename(), source.getContentType(),
                    storageKey, source.getSizeBytes(), source.getDurationSeconds());
            entity.setClientId(clientId);
            VoiceNote saved = voiceNoteRepository.save(entity);

            eventPublisher.publishEvent(new VoiceNoteCreatedEvent(saved.getId(), saved.getOwnerId(), saved.getNoteId()));
            return new VoiceUpload(voiceNoteMapper.toResponse(saved), true);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a retry of the same copy: its clip is the answer, this object is surplus.
            fileStorageService.delete(storageKey);
            return voiceNoteRepository.findByOwnerIdAndClientId(userId, clientId)
                    .map(clip -> new VoiceUpload(voiceNoteMapper.toResponse(clip), false))
                    .orElseThrow(() -> e);
        } catch (RuntimeException e) {
            fileStorageService.delete(storageKey);
            throw e;
        }
    }

    @Override
    @Transactional
    public boolean attachStandaloneClip(Long ownerId, Long clipId, Long noteId) {
        return voiceNoteRepository.attachStandalone(clipId, ownerId, noteId, java.time.LocalDateTime.now()) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceNoteResponse getVoiceNote(Long ownerId, Long voiceNoteId) {
        return voiceNoteMapper.toResponse(requireOwned(ownerId, voiceNoteId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<VoiceNoteResponse> listVoiceNotes(Long ownerId, Pageable pageable) {
        Page<VoiceNote> page = voiceNoteRepository.findByOwnerId(ownerId, pageable);
        List<VoiceNoteResponse> content = page.getContent().stream()
                .map(voiceNoteMapper::toResponse)
                .toList();
        return PagedResponse.of(content, page);
    }

    /**
     * Voice notes attached to a specific text note — the OWNER'S OWN view
     * of a note's recordings (`GET /voice-notes/by-note/{noteId}`, what
     * NoteDetailPage calls when the note's owner opens it).
     *
     * <p>PREVIOUSLY filtered {@code findByOwnerIdAndNoteId(ownerId, noteId)}
     * — "voice notes I personally own, attached to this note." That was
     * correct back when every recording on a note was necessarily the
     * note owner's own, but it silently excluded a WRITE collaborator's
     * reply once note-attached recordings could come from someone else:
     * the recording's OWN {@code ownerId} is the collaborator who made it,
     * not the note's owner, so the old filter's second half never matched
     * it. The owner would see a collaborator's TEXT edits arrive (those
     * route through the note itself, not per-row ownership) but never
     * their voice replies — a correctness gap, not a deliberate
     * permission boundary.
     *
     * <p>Fixed to mirror {@link #getAuthorizedVoiceNotesByNote}, which
     * already gets this right on the RECIPIENT'S side (SharedNoteService
     * calls it after confirming a grant on the note, returning every
     * attachment regardless of who recorded it). {@code ownerId} here
     * still matters — it is now what proves the caller may see this
     * note's attachments AT ALL ({@link NoteService#getNote} throws 404
     * for a note the caller doesn't own), rather than filtering rows by
     * it. Once that ownership gate passes, every voice note on the note
     * comes back, owner's and every collaborator's alike.
     */
    @Override
    @Transactional(readOnly = true)
    public List<VoiceNoteResponse> listVoiceNotesForNote(Long ownerId, Long noteId) {
        // Ownership check only — the returned NoteResponse itself is
        // unused here; NoteService#getNote throws 404 if noteId is not
        // this caller's own active note, which is the only thing this
        // call is for.
        noteService.getNote(ownerId, noteId);

        return voiceNoteRepository.findByNoteId(noteId).stream()
                .map(voiceNoteMapper::toResponse)
                .toList();
    }

    /**
     * Loads the audio bytes for `/download` and `/stream` alike (both
     * controller methods call this).
     *
     * <p>PREVIOUSLY strict {@link #requireOwned}: the caller had to be this
     * specific recording's own uploader. That was correct back when every
     * recording on a note was necessarily the note owner's own — the same
     * assumption {@link #listVoiceNotesForNote}'s own doc comment describes
     * going stale. Once a WRITE collaborator's reply could be attached to
     * someone else's note, the note owner could SEE that attachment (that
     * method was fixed already) but not PLAY it: this method still 404'd
     * because the owner is not that recording's uploader. Playback silently
     * regressed behind a passing list.
     *
     * <p>Fixed the same way: try direct ownership first (the common case,
     * and the only case for a standalone voice note — {@code noteId ==
     * null} short-circuits straight to not-found rather than asking
     * {@link NoteService} about a note that doesn't exist). Failing that,
     * fall back to "does the caller own the NOTE this recording is attached
     * to" — {@link NoteService#getNote} throws 404 if not, matching
     * {@link #listVoiceNotesForNote}'s exact authorization.
     */
    @Override
    @Transactional(readOnly = true)
    public VoiceNoteContent loadAudio(Long ownerId, Long voiceNoteId) {
        VoiceNote voiceNote = voiceNoteRepository.findById(voiceNoteId)
                .orElseThrow(() -> new VoiceNoteNotFoundException("Voice note not found."));

        if (!voiceNote.getOwnerId().equals(ownerId)) {
            if (voiceNote.getNoteId() == null) {
                throw new VoiceNoteNotFoundException("Voice note not found.");
            }
            // Throws (404-equivalent) if ownerId doesn't own this note either.
            noteService.getNote(ownerId, voiceNote.getNoteId());
        }

        Resource resource = fileStorageService.load(voiceNote.getStorageKey());
        return new VoiceNoteContent(resource, voiceNote.getContentType(), voiceNote.getOriginalFilename(), voiceNote.getSizeBytes());
    }

    @Override
    public void deleteVoiceNote(Long ownerId, Long voiceNoteId) {
        VoiceNote vn = requireOwned(ownerId, voiceNoteId);
        String storageKey = vn.getStorageKey();

        // Snapshot before the row is gone — same reasoning as every
        // share/voiceshare event: this is the only record of what it was
        // called once the delete below runs.
        Long deletedVoiceNoteId = vn.getId();
        String voiceNoteTitle = vn.getOriginalFilename();

        // Remove the metadata row first (Spring Data's delete is its own
        // transaction). Once that commits, nothing references the file, so
        // deleting the bytes second means a failure here leaves at most a
        // harmless orphaned file — never a row pointing at missing bytes.
        voiceNoteRepository.delete(vn);

        // Published here, right after the row is confirmed gone — deliberately
        // NOT wrapped in the storage cleanup's best-effort try/catch below. A
        // storage hiccup must not stop grant cleanup from firing; the voice
        // note IS gone as far as the rest of the app is concerned regardless
        // of whether the orphaned file gets swept up.
        eventPublisher.publishEvent(new VoiceNoteDeletedEvent(deletedVoiceNoteId, voiceNoteTitle, ownerId));

        // Best-effort: the logical delete already succeeded, so a storage hiccup
        // must not fail the request. Worst case is a leftover file for a later
        // cleanup sweep. (For multi-step DB transactions, the more correct tool
        // is a TransactionSynchronization afterCommit hook — worth knowing.)
        try {
            fileStorageService.delete(storageKey);
        } catch (RuntimeException ignored) {
            // swallow — orphaned file is tolerable; the note is gone as requested
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClipChange> findClipChangesSince(Long ownerId, LocalDateTime since, Pageable pageable) {
        // The order is part of the query; only the page number and size are taken from the caller.
        Pageable paging = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<VoiceNote> page = since == null
                ? voiceNoteRepository.findByOwnerIdAndNoteIdIsNotNullOrderByUpdatedAtAscIdAsc(ownerId, paging)
                : voiceNoteRepository.findClipChangesSince(ownerId, since, paging);
        return page.map(clip -> new ClipChange(
                clip.getId(), clip.getNoteId(), clip.getClientId(), clip.getDurationSeconds(),
                clip.getSizeBytes(), clip.getContentType(), clip.isDeleted()));
    }

    @Override
    public void removeClip(Long ownerId, Long clipId) {
        VoiceNote clip = requireOwned(ownerId, clipId);
        String storageKey = clip.getStorageKey();

        // Hidden, not deleted: the row is what tells the other devices. Auditing moves updatedAt.
        clip.setDeleted(true);
        clip.setRemoved(true);
        clip.setDeletedAt(LocalDateTime.now());
        voiceNoteRepository.save(clip);

        // Shares of the clip go with it, through the same event a single delete uses.
        eventPublisher.publishEvent(new VoiceNoteDeletedEvent(clip.getId(), clip.getOriginalFilename(), ownerId));

        // Best-effort, as in deleteVoiceNote: the row is already hidden. A permanent delete of the
        // note removes the row later and tolerates a file that is already gone.
        try {
            fileStorageService.delete(storageKey);
        } catch (RuntimeException ignored) {
            // A leftover file is tolerable.
        }
    }

    @Override
    @Transactional
    public void touchClip(Long clipId) {
        voiceNoteRepository.touch(clipId, LocalDateTime.now());
    }

    private String storeBytes(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return fileStorageService.store(in, file.getContentType(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new StorageException("Failed to read the uploaded file.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceNoteResponse getAuthorizedVoiceNote(Long voiceNoteId) {
        VoiceNote voiceNote = voiceNoteRepository.findById(voiceNoteId)
                .orElseThrow(() -> new VoiceNoteNotFoundException("Voice note not found."));
        return voiceNoteMapper.toResponse(voiceNote);
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceNoteContent getAuthorizedAudio(Long voiceNoteId) {
        VoiceNote voiceNote = voiceNoteRepository.findById(voiceNoteId)
                .orElseThrow(() -> new VoiceNoteNotFoundException("Voice note not found."));
        Resource resource = fileStorageService.load(voiceNote.getStorageKey());
        return new VoiceNoteContent(resource, voiceNote.getContentType(), voiceNote.getOriginalFilename(), voiceNote.getSizeBytes());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, VoiceNoteResponse> getAuthorizedVoiceNoteSummaries(Collection<Long> voiceNoteIds) {
        if (voiceNoteIds == null || voiceNoteIds.isEmpty()) {
            // Guard the empty case explicitly: an IN () clause is invalid SQL on
            // some databases, and there's nothing to ask for anyway.
            return Map.of();
        }
        return voiceNoteRepository.findByIdIn(voiceNoteIds).stream()
                .collect(Collectors.toMap(VoiceNote::getId, voiceNoteMapper::toResponse));
    }

    private VoiceNote requireOwned(Long ownerId, Long voiceNoteId) {
        return voiceNoteRepository.findByIdAndOwnerId(voiceNoteId, ownerId)
                .orElseThrow(() -> new VoiceNoteNotFoundException("Voice note not found."));
    }
    @Override
    @Transactional(readOnly = true)
    public List<VoiceNoteResponse> getAuthorizedVoiceNotesByNote(Long noteId) {
        return voiceNoteRepository.findByNoteId(noteId).stream()
                .map(voiceNoteMapper::toResponse)
                .toList();
    }
}
