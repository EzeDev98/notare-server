package com.notare.feature.share.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InsufficientNotePermissionException;
import com.notare.common.exception.NoteShareNotFoundException;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.share.dto.response.SharedNoteDetailResponse;
import com.notare.feature.share.dto.response.SharedNoteSummaryResponse;
import com.notare.feature.share.enums.NoteAccess;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.mapper.NoteShareMapper;
import com.notare.feature.share.model.NoteShare;
import com.notare.feature.share.repository.NoteShareRepository;
import com.notare.feature.share.service.SharedNoteService;
import com.notare.feature.user.dto.response.UserSummaryResponse;
import com.notare.feature.user.service.UserService;
import com.notare.feature.voiceshare.dto.request.ShareVoiceNoteRequest;
import com.notare.feature.voiceshare.service.VoiceShareService;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.dto.response.VoiceUpload;
import com.notare.feature.voicenote.service.VoiceNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SharedNoteServiceImpl implements SharedNoteService {

    private final NoteShareRepository noteShareRepository;
    private final NoteShareMapper noteShareMapper;

    /** Cross-module, PUBLIC API. This is the only class that calls the pre-authorized accessors. */
    private final NoteService noteService;

    /**
     * Cross-module, PUBLIC API. Added for the note-attached-voice-notes
     * capability: listing a note's attachments and letting a WRITE
     * collaborator record a new one directly onto a shared note.
     */
    private final VoiceNoteService voiceNoteService;

    /**
     * Also cross-module, PUBLIC API. Used ONLY to create the courtesy grant
     * back to the note's owner after a collaborator's upload — never to
     * gate access to an EXISTING voice note, which stays this class's own
     * job via requireGrant on the parent note.
     */
    private final VoiceShareService voiceShareService;

    /** Cross-module, PUBLIC API. Only to turn an owner id into a display name (step 15g). */
    private final UserService userService;

    /** Announces that a recipient left (step 17f). */
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /** Cross-module, PUBLIC API. Only to ask whether a clip was shared with the caller (step 17f). */
    private final com.notare.feature.voiceshare.service.SharedVoiceNoteService sharedVoiceNoteService;

    /**
     * Resolves a page of grants into a page of notes with ONE extra query for the
     * notes and one for the owners, not one per row.
     *
     * <p>The page itself comes from findVisibleSharedWithUser, which joins the note,
     * so a trashed note is absent from the rows and from the total, and the order is
     * the note's updatedAt, newest first (step 15g). A client-supplied sort is
     * ignored on purpose: it could only name grant columns, and the order people want
     * here is "most recently changed".
     */
    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SharedNoteSummaryResponse> listSharedWithMe(Long userId, Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<NoteShare> page = noteShareRepository.findVisibleSharedWithUser(userId, unsorted);

        List<NoteShare> shares = page.getContent();
        Map<Long, NoteSummaryResponse> summaries = noteService.getAuthorizedNoteSummaries(
                shares.stream().map(NoteShare::getNoteId).toList());
        Map<Long, String> owners = ownerNames(
                shares.stream().map(NoteShare::getOwnerId).collect(Collectors.toSet()));

        List<SharedNoteSummaryResponse> content = shares.stream()
                .map(share -> {
                    NoteSummaryResponse summary = summaries.get(share.getNoteId());
                    // Trashed between the page query and the batch read: skip the row.
                    return summary == null
                            ? null
                            : noteShareMapper.toSharedSummary(share, summary, owners.get(share.getOwnerId()));
                })
                .filter(Objects::nonNull)
                .toList();

        return PagedResponse.of(content, page);
    }

    @Override
    @Transactional(readOnly = true)
    public SharedNoteDetailResponse getSharedNote(Long userId, Long noteId) {
        NoteShare share = requireGrant(userId, noteId, SharePermission.READ);
        // Only reached once a grant is confirmed, which is the "authorized" in the accessor's name.
        return detail(share, noteService.getAuthorizedNote(noteId));
    }

    @Override
    @Transactional
    public SharedNoteDetailResponse updateSharedNote(Long userId, Long noteId, UpdateNoteRequest request) {
        NoteShare share = requireGrant(userId, noteId, SharePermission.WRITE);
        return detail(share, noteService.updateAuthorizedNote(noteId, request));
    }

    @Override
    @Transactional
    public SharedNoteDetailResponse autoSaveSharedNote(Long userId, Long noteId, AutoSaveNoteRequest request) {
        // WRITE, same as the full update. The size of the write is not the question the permission answers.
        NoteShare share = requireGrant(userId, noteId, SharePermission.WRITE);
        return detail(share, noteService.autoSaveAuthorizedNote(noteId, request));
    }

    private SharedNoteDetailResponse detail(NoteShare share, NoteResponse note) {
        String ownerName = ownerNames(Set.of(share.getOwnerId())).get(share.getOwnerId());
        return noteShareMapper.toDetail(share, note, ownerName);
    }

    /** Owner id to display name ("First Last", or the username when both are blank). No email, ever. */
    private Map<Long, String> ownerNames(Set<Long> ownerIds) {
        Map<Long, String> names = new HashMap<>();
        if (ownerIds.isEmpty()) {
            return names;
        }
        for (UserSummaryResponse user : userService.lookupUsers(ownerIds)) {
            String full = ((user.firstName() == null ? "" : user.firstName()) + " "
                    + (user.lastName() == null ? "" : user.lastName())).trim();
            names.put(user.id(), full.isEmpty() ? user.username() : full);
        }
        return names;
    }

    /**
     * The single authorization gate for this whole service. Two distinct
     * failures, two distinct answers:
     *
     * <ul>
     *   <li><b>No grant at all -> 404.</b> As far as this user is concerned the
     *       note doesn't exist, and the response must not suggest otherwise —
     *       a different status for "exists but not yours" would let anyone map
     *       out note ids by probing.</li>
     *   <li><b>Grant too weak -> 403.</b> A READ recipient attempting a write
     *       already knows the note exists; they can open it. Hiding it here
     *       would be incoherent, and the honest answer is "you may not do
     *       that".</li>
     * </ul>
     *
     * <p>The comparison goes through {@link SharePermission#satisfies} rather
     * than an equality check, so WRITE naturally satisfies a READ requirement
     * and the implication rule stays in one place.
     */
    private NoteShare requireGrant(Long userId, Long noteId, SharePermission required) {
        NoteShare share = noteShareRepository
                .findByNoteIdAndSharedWithUserId(noteId, userId)
                .orElseThrow(() -> new NoteShareNotFoundException("Note not found."));

        if (!share.getPermission().satisfies(required)) {
            throw new InsufficientNotePermissionException(
                    "You have read-only access to this note.");
        }
        return share;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoiceNoteResponse> listAttachedVoiceNotes(Long userId, Long noteId) {
        requireGrant(userId, noteId, SharePermission.READ);
        return voiceNoteService.getAuthorizedVoiceNotesByNote(noteId);
    }

    /**
     * Deliberately NOT @Transactional at this level, same reasoning as
     * VoiceNoteService#upload itself: file storage I/O must not happen while
     * holding a database connection open. The two writes this method makes —
     * the voice note row (inside voiceNoteService.upload, its own boundary)
     * and the courtesy VoiceShare grant (inside voiceShareService.shareVoiceNote,
     * its own @Transactional boundary) — are two separate, sequential
     * transactions, not one atomic unit. That is an accepted tradeoff, not an
     * oversight: seeing the tradeoff explained below.
     */
    @Override
    public VoiceNoteResponse uploadVoiceNote(Long userId, Long noteId, MultipartFile file, Integer durationSeconds) {
        NoteShare share = requireGrant(userId, noteId, SharePermission.WRITE);

        VoiceNoteResponse uploaded = voiceNoteService.upload(userId, file, noteId, durationSeconds);

        // Courtesy grant back to the note's owner, best-effort. The recording
        // itself has already succeeded and stored bytes on disk by this point;
        // failing the whole request over a grant-creation hiccup would leave
        // the recipient thinking their reply was lost when it wasn't. Worst
        // case on failure here: the note owner has to be re-shared this one
        // voice note by hand, or simply re-open the note (the note-attachment
        // listing above finds it regardless of any VoiceShare grant — the
        // grant only matters for the voice note's OWN direct endpoints).
        shareBackToOwner(userId, share.getOwnerId(), uploaded.id());

        return uploaded;
    }

    @Override
    public VoiceUpload uploadVoiceNoteToNote(Long userId, Long noteId, MultipartFile file, String clientId,
                                             Integer durationSeconds) {
        boolean owner = ownsActiveNote(userId, noteId);
        NoteShare share = owner ? null : requireGrant(userId, noteId, SharePermission.WRITE);

        VoiceUpload upload = voiceNoteService.uploadForNote(userId, noteId, file, clientId, durationSeconds);

        // A repeated upload changes nothing, so it grants nothing either.
        if (share != null && upload.created()) {
            shareBackToOwner(userId, share.getOwnerId(), upload.clip().id());
        }
        return upload;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NoteAccess> accessTo(Long userId, Long noteId) {
        if (ownsActiveNote(userId, noteId)) {
            return Optional.of(NoteAccess.OWNER);
        }
        Optional<NoteShare> grant = noteShareRepository.findByNoteIdAndSharedWithUserId(noteId, userId);
        if (grant.isEmpty() || !isActive(noteId)) {
            return Optional.empty();
        }
        boolean writes = grant.get().getPermission().satisfies(SharePermission.WRITE);
        return Optional.of(writes ? NoteAccess.EDITOR : NoteAccess.VIEWER);
    }

    /** True when the note exists and is not in the trash. Only asked once a grant has been found. */
    private boolean isActive(Long noteId) {
        try {
            noteService.getAuthorizedNote(noteId);
            return true;
        } catch (com.notare.common.exception.NoteNotFoundException ex) {
            return false;
        }
    }

    /** True when this user owns the note and it is not in the trash. */
    private boolean ownsActiveNote(Long userId, Long noteId) {
        try {
            noteService.getNote(userId, noteId);
            return true;
        } catch (com.notare.common.exception.NoteNotFoundException ex) {
            return false;
        }
    }

    /**
     * Courtesy grant back to the note's owner, best-effort. The recording itself has already
     * succeeded and stored its bytes by this point; failing the whole request over a grant hiccup
     * would leave the recorder thinking their clip was lost when it wasn't. Worst case on failure:
     * the owner still finds the clip, because the note's own listing returns every attachment
     * regardless of any VoiceShare grant. The grant only matters for the clip's own direct endpoints.
     */
    private void shareBackToOwner(Long userId, Long ownerId, Long voiceNoteId) {
        try {
            ShareVoiceNoteRequest grantRequest = new ShareVoiceNoteRequest();
            grantRequest.setSharedWithUserId(ownerId);
            grantRequest.setPermission(SharePermission.READ);
            voiceShareService.shareVoiceNote(userId, voiceNoteId, grantRequest);
        } catch (RuntimeException ex) {
            log.warn("Could not auto-share voice note {} back to note owner {}: {}",
                    voiceNoteId, ownerId, ex.getMessage());
        }
    }

    /**
     * Gated by the PARENT NOTE's grant, then a belt-and-suspenders check that
     * the voice note ACTUALLY belongs to that note — without it, a caller
     * with READ on note A could pass note A's id alongside note B's
     * voiceNoteId and stream note B's audio, so long as they could guess or
     * enumerate a valid voice note id. The path noteId must not be trusted
     * just because it FORMS a valid URL; it has to match the voice note's own
     * record of which note it's attached to.
     */
    @Override
    @Transactional(readOnly = true)
    public VoiceNoteContent getAttachedVoiceNoteAudio(Long userId, Long noteId, Long voiceNoteId) {
        requireGrant(userId, noteId, SharePermission.READ);

        VoiceNoteResponse voiceNote = voiceNoteService.getAuthorizedVoiceNote(voiceNoteId);
        if (!noteId.equals(voiceNote.noteId())) {
            throw new NoteShareNotFoundException("Note not found.");
        }

        return voiceNoteService.getAuthorizedAudio(voiceNoteId);
    }

    @Override
    @Transactional
    public void leaveNote(Long userId, Long noteId) {
        NoteShare share = noteShareRepository.findByNoteIdAndSharedWithUserId(noteId, userId)
                .orElseThrow(() -> new NoteShareNotFoundException("Note not found."));

        // The label has to be read before the grant goes. A trashed note has no readable body: no label then.
        String label = null;
        try {
            label = com.notare.common.util.NoteLabeler.deriveLabel(noteService.getAuthorizedNote(noteId).content());
        } catch (com.notare.common.exception.NoteNotFoundException ex) {
            log.debug("Note {} is gone; leaving without a label.", noteId);
        }

        noteShareRepository.delete(share);
        eventPublisher.publishEvent(
                new com.notare.feature.share.event.NoteShareRevokedEvent(
                        noteId, label, share.getOwnerId(), userId, true));
    }

    @Override
    public VoiceUpload copyVoiceNote(Long userId, Long voiceNoteId, Long targetNoteId, String clientId) {
        // The target must be the caller's own, live note. 404 either way: nobody learns another note exists.
        if (!ownsActiveNote(userId, targetNoteId)) {
            throw new com.notare.common.exception.NoteNotFoundException("Note not found.");
        }

        VoiceNoteResponse source = voiceNoteService.getAuthorizedVoiceNote(voiceNoteId);
        boolean readable = userId.equals(source.ownerId())
                || (source.noteId() != null && accessTo(userId, source.noteId()).isPresent())
                || isSharedWithUser(userId, voiceNoteId);
        if (!readable) {
            throw new com.notare.common.exception.VoiceNoteNotFoundException("Voice note not found.");
        }

        return voiceNoteService.copyClip(userId, voiceNoteId, targetNoteId, clientId);
    }

    /** True when this clip was shared with the user directly. */
    private boolean isSharedWithUser(Long userId, Long voiceNoteId) {
        try {
            sharedVoiceNoteService.getSharedVoiceNote(userId, voiceNoteId);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}