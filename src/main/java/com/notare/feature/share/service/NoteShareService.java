package com.notare.feature.share.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.share.dto.request.ShareNoteRequest;
import com.notare.feature.share.dto.request.UpdateSharePermissionRequest;
import com.notare.feature.share.dto.response.NoteShareResponse;
import org.springframework.data.domain.Pageable;

/**
 * The OWNER's side of sharing: granting access, adjusting it, taking it away,
 * and seeing who currently has it. Every method takes ownerId and every one is
 * scoped by it — an owner can only ever manage grants on their own notes.
 *
 * <p>The recipient's side (reading and writing notes shared with me) is a
 * separate service in 7D. They're split because they answer to different
 * people and enforce different rules: this one asks "is this your note?", that
 * one asks "do you have a grant, and is it strong enough?". Merging them would
 * put both authorization models in one class and make it easy to apply the
 * wrong one.
 */
public interface NoteShareService {

    /** Grant a user access to one of my notes. */
    NoteShareResponse shareNote(Long ownerId, Long noteId, ShareNoteRequest request);

    /** Promote or demote an existing grant (READ <-> WRITE). */
    NoteShareResponse updatePermission(Long ownerId,
                                       Long noteId,
                                       Long sharedWithUserId,
                                       UpdateSharePermissionRequest request);

    /** Withdraw a grant entirely. */
    void revoke(Long ownerId, Long noteId, Long sharedWithUserId);

    /** Who currently has access to one of my notes. */
    PagedResponse<NoteShareResponse> listShares(Long ownerId, Long noteId, Pageable pageable);
}