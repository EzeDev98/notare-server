package com.notare.feature.note.dto.response;

import java.time.LocalDateTime;

/** A note as shown in a list. */
public record NoteSummaryResponse(
        Long id,
        String contentPreview,
        boolean pinned,
        Long version,
        LocalDateTime updatedAt
) {
}
