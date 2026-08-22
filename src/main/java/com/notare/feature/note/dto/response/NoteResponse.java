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
 *
 * <p>Step 10 adds lastModifiedBy here and NOWHERE else. It's on the detail
 * response because that's where "someone else edited this" is worth reading,
 * and it reaches the shared-note endpoints for free, since those return this
 * same record — the payoff for having nested rather than duplicated it.
 *
 * <p>It is deliberately NOT on NoteSummaryResponse. In an owner's own list the
 * value is themselves on almost every row, so it would be a column of noise
 * paid for on every page load.
 */
public record NoteResponse(
        Long id,
        Long ownerId,
        String content,
        boolean pinned,
        boolean archived,
        Long version,

        /**
         * Who last wrote to this note — the owner, or a collaborator holding
         * WRITE. A raw id, matching actorId on notifications and
         * sharedWithUserId on shares: this project consistently returns ids and
         * lets the client resolve people, rather than embedding profiles and
         * paying for a user lookup per row.
         *
         * <p>Null on notes written before Step 10. May also be 0 — the reserved
         * system auditor — so a client must special-case it rather than trying
         * to render "user 0".
         */
        Long lastModifiedBy,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}