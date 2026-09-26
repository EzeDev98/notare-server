package com.notare.feature.sync.enums;

/**
 * How one operation in a batch turned out.
 *
 * <p>Per-operation, not per-batch — that's the whole point of the design. One
 * stale edit in a queue of twenty must not block the other nineteen, so the
 * HTTP status is 200 for the batch and the real outcome lives here, once per
 * operation.
 *
 * <p>The client's handling differs sharply per value, which is why these are
 * four distinct constants rather than a boolean plus a message: APPLIED means
 * drop it from the queue, CONFLICT means keep the local copy and ask the user,
 * NOT_FOUND means drop it silently, INVALID means it will never succeed and
 * belongs in a bug report.
 */
public enum SyncResultStatus {

    /**
     * It worked — or it had already worked, and this was a replay. Both report
     * APPLIED, because from the client's side they are the same fact: the
     * change is on the server, stop trying to send it. Distinguishing "applied
     * now" from "applied earlier" would give the client a difference it has no
     * use for.
     */
    APPLIED,

    /**
     * The note is gone — trashed or deleted on another device while this one
     * was offline. Not an error the user should see: the operation is simply
     * moot, and the client drops it.
     */
    NOT_FOUND,

    /**
     * The operation is malformed — an UPDATE with no version, a CREATE with no
     * clientId. Unlike a conflict, retrying will never help. This means a bug
     * in the client, and the message says which field.
     */
    INVALID
}