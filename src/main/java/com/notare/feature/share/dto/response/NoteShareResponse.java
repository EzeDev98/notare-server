package com.notare.feature.share.dto.response;

import com.notare.feature.share.enums.SharePermission;

import java.time.LocalDateTime;

/**
 * A grant as the OWNER sees it: "this note, that person, this level".
 *
 * <p>Returned from sharing, changing a permission, and listing a note's
 * recipients. Like CollaborationResponse, it carries raw user ids rather than
 * embedding profiles — enriching each row would mean a user lookup per row,
 * which is an N+1 on the listing endpoint. The client already has user search
 * to resolve ids to names, and can batch that itself.
 */
public record NoteShareResponse(
        Long id,
        Long noteId,
        Long ownerId,
        Long sharedWithUserId,
        SharePermission permission,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}