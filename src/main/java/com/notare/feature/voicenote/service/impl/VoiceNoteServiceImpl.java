package com.notare.feature.voicenote.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.StorageException;
import com.notare.common.exception.VoiceNoteNotFoundException;
import com.notare.common.storage.FileStorageService;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.mapper.VoiceNoteMapper;
import com.notare.feature.voicenote.model.VoiceNote;
import com.notare.feature.voicenote.repository.VoiceNoteRepository;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voicenote.validation.VoiceUploadValidator;
import lombok.RequiredArgsConstructor;
import com.notare.feature.voicenote.event.VoiceNoteCreatedEvent;
import com.notare.feature.voicenote.event.VoiceNoteDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
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

    @Override
    public VoiceNoteResponse upload(Long ownerId, MultipartFile file, Long noteId, Integer durationSeconds) {
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
            VoiceNote saved = voiceNoteRepository.save(entity);

            // Announce the creation only after the metadata row exists.
            // Consumers such as the sharing module can now grant access to
            // this recording when its parent note is already shared.
            eventPublisher.publishEvent(new VoiceNoteCreatedEvent(
                    saved.getId(),
                    saved.getOwnerId(),
                    saved.getNoteId()));

            return voiceNoteMapper.toResponse(saved);
        } catch (RuntimeException e) {
            fileStorageService.delete(storageKey);
            throw e;
        }
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

    @Override
    @Transactional(readOnly = true)
    public List<VoiceNoteResponse> listVoiceNotesForNote(Long ownerId, Long noteId) {
        return voiceNoteRepository.findByOwnerIdAndNoteId(ownerId, noteId).stream()
                .map(voiceNoteMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceNoteContent loadAudio(Long ownerId, Long voiceNoteId) {
        VoiceNote voiceNote = requireOwned(ownerId, voiceNoteId);
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