package com.notare.feature.sync.dto.response;

import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.sync.enums.SyncResultStatus;

/**
 * The outcome of one operation in a batch.
 *
 * <p>Echoing operationId back is what lets the client match results to queue
 * entries. It cannot rely on array position: it's the only stable key, and a
 * client that pairs by index will eventually pair wrongly.
 *
 * <p><b>note is populated on APPLIED and on CONFLICT, and that's the design
 * decision of Step 9.</b> On APPLIED it carries the server's id and new version
 * — which is how a CREATE's temporary clientId gets reconciled to a real id.
 * On CONFLICT it carries the server's CURRENT note, title and content included.
 *
 * <p>Returning the full note on conflict rather than just a version number is
 * the difference between a usable offline app and a frustrating one. With only
 * a version, a client facing twenty conflicts must make twenty follow-up
 * requests before it can show the user anything. With the content in hand it
 * can render a diff immediately, offline-to-online in one round trip. The cost
 * is a larger response on the unhappy path, which is exactly where the extra
 * bytes are worth paying for.
 */
public record SyncOperationResult(

        /** Echoed from the request so the client can match this to its queue entry. */
        String operationId,

        SyncResultStatus status,

        /**
         * APPLIED: the note as it now stands (id + version for reconciliation).
         * CONFLICT: the server's current note, for diffing.
         * NOT_FOUND / INVALID: null.
         */
        NoteResponse note,

        /**
         * The clientId echoed back on a CREATE, so the client can map its local
         * note to note.id(). Null for other types. It's also on the note row,
         * but NoteResponse deliberately doesn't expose it — that DTO belongs to
         * the note module and has no business carrying a sync concern.
         */
        String clientId,

        /**
         * Human-readable detail, mainly for INVALID (which field was wrong) and
         * CONFLICT. Null when there's nothing to add. NOT for display verbatim
         * to end users on INVALID — that path means a client bug.
         */
        String message
) {
}