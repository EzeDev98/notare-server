package com.notare.feature.share.dto.response;

import java.time.LocalDateTime;

/**
 * A row in "notes shared with me" (step 15g, B-13).
 *
 * <p>Flat, and deliberately without the owner's pinned, archived or version, which
 * the nested NoteSummaryResponse used to carry into a recipient's list. A
 * recipient sees what the note says, who gave it to them, what they may do with
 * it, and when it last changed.
 */
public record SharedNoteSummaryResponse(
        Long shareId,
        Long noteId,
        String title,
        String contentPreview,
        String ownerName,
        String role,
        LocalDateTime updatedAt,
        LocalDateTime sharedAt
) {
}
