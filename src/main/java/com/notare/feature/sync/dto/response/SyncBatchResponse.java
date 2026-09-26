package com.notare.feature.sync.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The result of replaying a queue: one entry per submitted operation, in the
 * same order.
 *
 * <p>The HTTP status is 200 even when every operation conflicted. That reads
 * oddly at first, but it's correct: the BATCH was processed successfully, and
 * what happened to each operation is application state, not a transport
 * failure. Returning 409 for the batch would force the client to parse an error
 * body to find the nineteen operations that actually succeeded.
 */
public record SyncBatchResponse(

        List<SyncOperationResult> results,

        /**
         * Convenience counts so a client can decide whether to prompt the user
         * without walking the list.
         */
        int appliedCount,

        /**
         * The server's clock at the end of the batch, offered as the next pull
         * cursor.
         *
         * <p>The SERVER's time, never the client's — device clocks drift, and a
         * cursor from a device running five minutes fast would skip five
         * minutes of changes permanently. This is also why the pull window
         * overlaps rather than trusting any timestamp exactly.
         */
        LocalDateTime serverTime
) {
}