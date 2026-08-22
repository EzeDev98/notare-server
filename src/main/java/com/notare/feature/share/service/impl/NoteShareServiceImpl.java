package com.notare.feature.share.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InvalidNoteShareException;
import com.notare.common.exception.NoteAlreadySharedException;
import com.notare.common.exception.NoteShareNotFoundException;
import com.notare.common.util.NoteLabeler;
import com.notare.feature.collaboration.service.CollaborationService;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.share.dto.request.ShareNoteRequest;
import com.notare.feature.share.dto.request.UpdateSharePermissionRequest;
import com.notare.feature.share.dto.response.NoteShareResponse;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.event.NoteSharePermissionChangedEvent;
import com.notare.feature.share.event.NoteShareRevokedEvent;
import com.notare.feature.share.event.NoteSharedEvent;
import com.notare.feature.share.mapper.NoteShareMapper;
import com.notare.feature.share.model.NoteShare;
import com.notare.feature.share.repository.NoteShareRepository;
import com.notare.feature.share.service.NoteShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteShareServiceImpl implements NoteShareService {

    private final NoteShareRepository noteShareRepository;
    private final NoteShareMapper noteShareMapper;

    /** Cross-module dependencies, both PUBLIC service APIs — never the other modules' repositories. */
    private final NoteService noteService;
    private final CollaborationService collaborationService;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Four gates, in a deliberate order. Each is cheaper than the one after it,
     * or rules out a case that would make the next one meaningless:
     *
     * <ol>
     *   <li>Self-share — pure in-memory comparison, no query at all.</li>
     *   <li>Do I own this note? — via the note module's owner-scoped read, so a
     *       note that isn't mine is a 404. This runs BEFORE the collaborator
     *       check on purpose: otherwise probing someone else's note id with a
     *       non-collaborator would return 400 while probing with a real
     *       collaborator returned 404, and the difference would confirm which
     *       note ids exist.</li>
     *   <li>Is the recipient an accepted collaborator of mine? — Step 6's gate.</li>
     *   <li>Is it already shared? — friendly 409 before the unique constraint fires.</li>
     * </ol>
     */
    @Override
    @Transactional
    public NoteShareResponse shareNote(Long ownerId, Long noteId, ShareNoteRequest request) {
        Long recipientId = request.getSharedWithUserId();

        if (ownerId.equals(recipientId)) {
            throw new InvalidNoteShareException("You cannot share a note with yourself.");
        }

        // Ownership check through the note module's PUBLIC API. Throws 404 if the
        // note isn't mine or is in the trash — the same answer either way, so
        // nothing is revealed about other people's notes.
        //
        // The result is now KEPT rather than discarded: its content is what the
        // notification's label will be derived from, and this is the only moment
        // it's guaranteed to be the content the owner was actually looking at.
        NoteResponse note = noteService.getNote(ownerId, noteId);

        // Step 6's relationship is the prerequisite. Note this asks for ACCEPTED
        // specifically: a pending invitation isn't consent, and a rejected one is
        // a refusal.
        if (!collaborationService.isAcceptedCollaborator(ownerId, recipientId)) {
            throw new InvalidNoteShareException(
                    "You can only share notes with people who have accepted your collaboration invitation.");
        }

        if (noteShareRepository.existsByNoteIdAndSharedWithUserId(noteId, recipientId)) {
            throw new NoteAlreadySharedException(
                    "This note is already shared with that user. Update the permission instead.");
        }

        NoteShare share = new NoteShare();
        share.setNoteId(noteId);
        share.setOwnerId(ownerId);
        share.setSharedWithUserId(recipientId);
        share.setPermission(request.getPermission());

        try {
            NoteShare saved = noteShareRepository.save(share);
            eventPublisher.publishEvent(new NoteSharedEvent(
                    saved.getId(), noteId, NoteLabeler.deriveLabel(note.content()), ownerId, recipientId, saved.getPermission()));
            return noteShareMapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // Lost a race with a concurrent identical share; uq_note_shares_note_user
            // fired. Same 409 the pre-check would have given. The constraint is the
            // real guard — the check above just makes the common case friendlier.
            throw new NoteAlreadySharedException(
                    "This note is already shared with that user. Update the permission instead.");
        }
    }

    /**
     * Changing a level is a separate operation from creating a grant, which is
     * why re-POSTing a share returns 409 rather than quietly upgrading it. A
     * POST that silently escalates READ to WRITE is a permission change
     * disguised as a create — easy to trigger by accident from a retry, and
     * invisible in logs as anything but a duplicate request.
     */
    @Override
    @Transactional
    public NoteShareResponse updatePermission(Long ownerId,
                                              Long noteId,
                                              Long sharedWithUserId,
                                              UpdateSharePermissionRequest request) {

        NoteShare share = requireOwnedShare(ownerId, noteId, sharedWithUserId);
        SharePermission target = request.getPermission();

        SharePermission previous = share.getPermission();

        if (previous == target) {
            // Idempotent: setting the level it already has is a no-op, and
            // skipping the write avoids a pointless updatedAt bump. No event
            // either — nothing changed, so there is nothing to announce.
            return noteShareMapper.toResponse(share);
        }

        share.setPermission(target);
        // saveAndFlush so updatedAt is refreshed before mapping the response.
        NoteShare saved = noteShareRepository.saveAndFlush(share);

        // One extra read to get the content the notification's label is derived
        // from. It also re-confirms ownership, so it isn't purely overhead — and
        // changing a permission is a rare, deliberate action, not a hot path.
        NoteResponse note = noteService.getNote(ownerId, noteId);

        eventPublisher.publishEvent(new NoteSharePermissionChangedEvent(
                saved.getId(), noteId, NoteLabeler.deriveLabel(note.content()), ownerId, sharedWithUserId, previous, target));

        return noteShareMapper.toResponse(saved);
    }

    /**
     * Revoke by fetching the owner-scoped row first, then deleting the managed
     * entity — never deleteById. Same rule as permanentlyDeleteNote: deleting by
     * primary key alone would skip the ownership check and let anyone drop a
     * grant they don't control.
     */
    @Override
    @Transactional
    public void revoke(Long ownerId, Long noteId, Long sharedWithUserId) {
        NoteShare share = requireOwnedShare(ownerId, noteId, sharedWithUserId);

        // Read the content BEFORE deleting. Once the grant is gone the recipient
        // can no longer fetch this note at all, so the label snapshotted into
        // their notification is the only way they'll know which note they lost.
        NoteResponse note = noteService.getNote(ownerId, noteId);

        noteShareRepository.delete(share);

        eventPublisher.publishEvent(
                new NoteShareRevokedEvent(noteId, NoteLabeler.deriveLabel(note.content()), ownerId, sharedWithUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<NoteShareResponse> listShares(Long ownerId, Long noteId, Pageable pageable) {
        // Verify ownership explicitly rather than relying on the scoped query
        // below returning an empty page. Both are safe, but an empty page is
        // ambiguous — "no one has access" and "that isn't your note" would look
        // identical to the owner, including when they've simply mistyped an id.
        // The 404 says which. The query stays owner-scoped anyway: two
        // independent checks, so a future refactor that drops one still leaves
        // the other standing.
        noteService.getNote(ownerId, noteId);

        Page<NoteShare> page = noteShareRepository.findByNoteIdAndOwnerId(noteId, ownerId, pageable);
        List<NoteShareResponse> content = page.getContent().stream()
                .map(noteShareMapper::toResponse)
                .toList();
        return PagedResponse.of(content, page);
    }

    /**
     * Loads a grant addressed by (note, recipient) and scoped to the owner. The
     * scoping is in the QUERY, so a grant on someone else's note comes back
     * empty and surfaces as 404 — the caller never learns it exists.
     */
    private NoteShare requireOwnedShare(Long ownerId, Long noteId, Long sharedWithUserId) {
        return noteShareRepository
                .findByNoteIdAndOwnerIdAndSharedWithUserId(noteId, ownerId, sharedWithUserId)
                .orElseThrow(() -> new NoteShareNotFoundException("This note is not shared with that user."));
    }
}