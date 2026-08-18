package com.notare.feature.voiceshare.dto.response;

import com.notare.feature.share.enums.SharePermission;

import java.time.LocalDateTime;

/**
 * A grant as the OWNER sees it: "this voice note, that person, this level".
 *
 * <p>Mirrors {@link com.notare.feature.share.dto.response.NoteShareResponse}.
 * Returned from sharing, changing a permission, and listing a voice note's
 * recipients. Carries raw user ids rather than embedding profiles —
 * enriching each row would mean a user lookup per row, which is an N+1 on
 * the listing endpoint. The client already has the batch user-lookup feature
 * to resolve ids to names.
 */
public record VoiceShareResponse(
        Long id,
        Long voiceNoteId,
        Long ownerId,
        Long sharedWithUserId,
        SharePermission permission,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
