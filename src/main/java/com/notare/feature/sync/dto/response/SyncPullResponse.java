package com.notare.feature.sync.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything that changed for this user since the client's cursor.
 *
 * <p><b>Changes and tombstones are two separate lists</b>, rather than one list
 * of notes each carrying a deleted flag. Two reasons. First, it preserves a
 * Step 1 invariant: NoteResponse describes an ACTIVE note and deliberately
 * exposes no deleted/deletedAt, so bending it into a tombstone would leak
 * internal state into every note payload in the app. Second, a client does
 * genuinely different things with them — merge one, evict the other — and a
 * shape that forces a branch on a boolean invites forgetting the branch.
 *
 * <p>Tombstones are bare ids because that's all eviction needs. Shipping the
 * title and content of a note the user just trashed would be pointless payload
 * on every sync.
 */
public record SyncPullResponse(

        /** Created or updated since the cursor. */
        List<SyncNoteResponse> changed,

        /** Trashed since the cursor — the client drops these from its cache. */
        List<Long> deletedIds,

        /**
         * The cursor to send next time. Always the SERVER's clock, never the
         * client's, and always taken from this response rather than computed
         * locally — a device with a fast clock would otherwise skip changes
         * permanently.
         */
        LocalDateTime nextCursor,

        /**
         * True when more changes are waiting beyond this page.
         *
         * <p>A client that stops on hasMore = true and comes back later is fine
         * — but it must NOT advance its cursor past what it has actually
         * merged, or the remainder is skipped forever. Making this explicit in
         * the payload is what stops "I got a full page, I'm done" from
         * silently losing the tail of a large first sync.
         */
        boolean hasMore
) {
}