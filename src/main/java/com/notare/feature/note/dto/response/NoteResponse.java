package com.notare.feature.note.dto.response;

import java.time.LocalDateTime;

/**
 * Full representation of a single note, returned by get-one, create and update.
 * A record because a response is built once and never mutated.
 *
 * Note the fields we DON'T expose: no `deleted` / `deletedAt`. The get-one
 * endpoint only ever returns active notes, so those would always be false/null
 * and just leak internal state. `version` IS exposed on purpose: the client
 * sends it back on update in step 2 so we can detect edit conflicts.
 */
public record NoteResponse(
        Long id,
        Long ownerId,
        String title,
        String content,
        boolean pinned,
        boolean archived,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}