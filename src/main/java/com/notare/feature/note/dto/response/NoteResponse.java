package com.notare.feature.note.dto.response;

import java.time.LocalDateTime;

/** A note in full, with its AI title. */
public record NoteResponse(
        Long id,
        Long ownerId,
        String content,
        boolean pinned,
        Long version,

        Long lastModifiedBy,

        LocalDateTime createdAt,
        LocalDateTime updatedAt,

        boolean restorable,

        String aiTitle
) {
}
