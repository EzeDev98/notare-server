package com.notare.feature.voiceshare.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InvalidVoiceShareException;
import com.notare.common.exception.VoiceAlreadySharedException;
import com.notare.common.exception.VoiceShareNotFoundException;
import com.notare.feature.collaboration.service.CollaborationService;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voiceshare.dto.request.ShareVoiceNoteRequest;
import com.notare.feature.voiceshare.dto.request.UpdateVoiceSharePermissionRequest;
import com.notare.feature.voiceshare.dto.response.VoiceShareResponse;
import com.notare.feature.voiceshare.mapper.VoiceShareMapper;
import com.notare.feature.voiceshare.model.VoiceShare;
import com.notare.feature.voiceshare.repository.VoiceShareRepository;
import com.notare.feature.voiceshare.service.VoiceShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Mirrors {@link com.notare.feature.share.service.impl.NoteShareServiceImpl}
 * 1:1, including the four-gate order in {@link #shareVoiceNote}.
 *
 * <p>Event publishing (VoiceNoteSharedEvent / VoiceSharePermissionChangedEvent
 * / VoiceShareRevokedEvent) is intentionally NOT wired in yet — those event
 * classes are Step 6a. Each spot where NoteShareServiceImpl publishes is
 * marked below with a TODO(Step 6) so the wiring is a small, obvious diff
 * rather than a re-read of this whole class.
 */
@Service
@RequiredArgsConstructor
public class VoiceShareServiceImpl implements VoiceShareService {

    private final VoiceShareRepository voiceShareRepository;
    private final VoiceShareMapper voiceShareMapper;

    /** Cross-module dependencies, both PUBLIC service APIs — never the other modules' repositories. */
    private final VoiceNoteService voiceNoteService;
    private final CollaborationService collaborationService;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Four gates, in the same deliberate order as NoteShareServiceImpl:
     *
     * <ol>
     *   <li>Self-share — pure in-memory comparison, no query at all.</li>
     *   <li>Do I own this voice note? — via the voicenote module's owner-scoped
     *       read, so a voice note that isn't mine is a 404. Runs BEFORE the
     *       collaborator check so probing someone else's id doesn't leak which
     *       ids exist via a differing status code.</li>
     *   <li>Is the recipient an accepted collaborator of mine?</li>
     *   <li>Is it already shared? — friendly 409 before the unique constraint fires.</li>
     * </ol>
     */
    @Override
    @Transactional
    public VoiceShareResponse shareVoiceNote(Long ownerId, Long voiceNoteId, ShareVoiceNoteRequest request) {
        Long recipientId = request.getSharedWithUserId();

        if (ownerId.equals(recipientId)) {
            throw new InvalidVoiceShareException("You cannot share a voice note with yourself.");
        }

        // Ownership check through the voicenote module's PUBLIC API. Throws 404
        // if the voice note isn't mine. Kept (not discarded) the same way
        // NoteShareServiceImpl keeps NoteResponse — the filename here is the
        // Step 6 event snapshot's equivalent of the note title.
        VoiceNoteResponse voiceNote = voiceNoteService.getVoiceNote(ownerId, voiceNoteId);

        if (!collaborationService.isAcceptedCollaborator(ownerId, recipientId)) {
            throw new InvalidVoiceShareException(
                    "You can only share voice notes with people who have accepted your collaboration invitation.");
        }

        if (voiceShareRepository.existsByVoiceNoteIdAndSharedWithUserId(voiceNoteId, recipientId)) {
            throw new VoiceAlreadySharedException(
                    "This voice note is already shared with that user. Update the permission instead.");
        }

        VoiceShare share = new VoiceShare();
        share.setVoiceNoteId(voiceNoteId);
        share.setOwnerId(ownerId);
        share.setSharedWithUserId(recipientId);
        share.setPermission(request.getPermission());

        try {
            VoiceShare saved = voiceShareRepository.save(share);
            eventPublisher.publishEvent(new com.notare.feature.voiceshare.event.VoiceNoteSharedEvent(
                    saved.getId(), voiceNoteId, voiceNote.originalFilename(), ownerId, recipientId, saved.getPermission()));
            return voiceShareMapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // Lost a race with a concurrent identical share;
            // uq_voice_shares_voice_note_user fired. Same 409 the pre-check
            // would have given.
            throw new VoiceAlreadySharedException(
                    "This voice note is already shared with that user. Update the permission instead.");
        }
    }

    /**
     * Changing a level is a separate operation from creating a grant, same
     * reasoning as NoteShareServiceImpl: re-POSTing a share returns 409 rather
     * than quietly upgrading it.
     */
    @Override
    @Transactional
    public VoiceShareResponse updatePermission(Long ownerId,
                                                Long voiceNoteId,
                                                Long sharedWithUserId,
                                                UpdateVoiceSharePermissionRequest request) {

        VoiceShare share = requireOwnedShare(ownerId, voiceNoteId, sharedWithUserId);
        SharePermission target = request.getPermission();

        SharePermission previous = share.getPermission();

        if (previous == target) {
            // Idempotent no-op, no event — nothing changed, so there is nothing
            // to announce. Also skips a pointless updatedAt bump.
            return voiceShareMapper.toResponse(share);
        }

        share.setPermission(target);
        // saveAndFlush so updatedAt is refreshed before mapping the response.
        VoiceShare saved = voiceShareRepository.saveAndFlush(share);

        // One extra read for the filename snapshot; also re-confirms ownership.
        // Not purely overhead — same tradeoff NoteShareServiceImpl makes.
        VoiceNoteResponse voiceNote = voiceNoteService.getVoiceNote(ownerId, voiceNoteId);

        eventPublisher.publishEvent(new com.notare.feature.voiceshare.event.VoiceSharePermissionChangedEvent(
                saved.getId(), voiceNoteId, voiceNote.originalFilename(), ownerId, sharedWithUserId, previous, target));

        return voiceShareMapper.toResponse(saved);
    }

    /**
     * Revoke by fetching the owner-scoped row first, then deleting the managed
     * entity — never deleteById. Same rule as NoteShareServiceImpl: deleting by
     * primary key alone would skip the ownership check.
     */
    @Override
    @Transactional
    public void revoke(Long ownerId, Long voiceNoteId, Long sharedWithUserId) {
        VoiceShare share = requireOwnedShare(ownerId, voiceNoteId, sharedWithUserId);

        // Read the filename BEFORE deleting — once the grant is gone the
        // recipient can no longer fetch this voice note at all, so the Step 6
        // notification snapshot is the only way they'll know which one they lost.
        VoiceNoteResponse voiceNote = voiceNoteService.getVoiceNote(ownerId, voiceNoteId);

        voiceShareRepository.delete(share);

        eventPublisher.publishEvent(new com.notare.feature.voiceshare.event.VoiceShareRevokedEvent(
                voiceNoteId, voiceNote.originalFilename(), ownerId, sharedWithUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<VoiceShareResponse> listShares(Long ownerId, Long voiceNoteId, Pageable pageable) {
        // Verify ownership explicitly rather than relying on the scoped query
        // below returning an empty page — same ambiguity-avoidance reasoning as
        // NoteShareServiceImpl.
        voiceNoteService.getVoiceNote(ownerId, voiceNoteId);

        Page<VoiceShare> page = voiceShareRepository.findByVoiceNoteIdAndOwnerId(voiceNoteId, ownerId, pageable);
        List<VoiceShareResponse> content = page.getContent().stream()
                .map(voiceShareMapper::toResponse)
                .toList();
        return PagedResponse.of(content, page);
    }

    /**
     * Loads a grant addressed by (voice note, recipient) and scoped to the
     * owner. The scoping is in the QUERY, so a grant on someone else's voice
     * note comes back empty and surfaces as 404.
     */
    private VoiceShare requireOwnedShare(Long ownerId, Long voiceNoteId, Long sharedWithUserId) {
        return voiceShareRepository
                .findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(voiceNoteId, ownerId, sharedWithUserId)
                .orElseThrow(() -> new VoiceShareNotFoundException("This voice note is not shared with that user."));
    }
}
