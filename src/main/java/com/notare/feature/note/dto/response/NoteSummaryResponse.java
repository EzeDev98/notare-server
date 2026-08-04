package com.notare.feature.note.dto.response;

import java.time.LocalDateTime;

/**
 * Lightweight representation for list views. Carries a short contentPreview
 * instead of the full content, so a page of 20 notes doesn't ship 20 full
 * note bodies over the wire. The preview is built in the mapper (step 1E).
 */
public record NoteSummaryResponse(
        Long id,
        String title,
        String contentPreview,
        boolean pinned,
        boolean archived,
        Long version,
        LocalDateTime updatedAt
) {
}