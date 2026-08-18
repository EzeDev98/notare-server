package com.notare.feature.voiceshare.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.voiceshare.dto.request.ShareVoiceNoteRequest;
import com.notare.feature.voiceshare.dto.request.UpdateVoiceSharePermissionRequest;
import com.notare.feature.voiceshare.dto.response.VoiceShareResponse;
import org.springframework.data.domain.Pageable;

/**
 * The OWNER's side of voice-note sharing: granting access, adjusting it,
 * taking it away, and seeing who currently has it. Mirrors
 * {@link com.notare.feature.share.service.NoteShareService} exactly, same
 * split reasoning: this asks "is this your voice note?"; the recipient-side
 * service (Step 5) asks "do you have a grant, and is it strong enough?".
 * Every method takes ownerId and every one is scoped by it.
 */
public interface VoiceShareService {

    /** Grant a user access to one of my voice notes. */
    VoiceShareResponse shareVoiceNote(Long ownerId, Long voiceNoteId, ShareVoiceNoteRequest request);

    /** Promote or demote an existing grant (READ <-> WRITE). */
    VoiceShareResponse updatePermission(Long ownerId,
                                         Long voiceNoteId,
                                         Long sharedWithUserId,
                                         UpdateVoiceSharePermissionRequest request);

    /** Withdraw a grant entirely. */
    void revoke(Long ownerId, Long voiceNoteId, Long sharedWithUserId);

    /** Who currently has access to one of my voice notes. */
    PagedResponse<VoiceShareResponse> listShares(Long ownerId, Long voiceNoteId, Pageable pageable);
}
