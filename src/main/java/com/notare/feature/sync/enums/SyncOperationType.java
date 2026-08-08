package com.notare.feature.sync.enums;

/**
 * What a queued offline operation is trying to do.
 *
 * <p>Three verbs, matching the three things a client can do to a note while
 * disconnected. Notably absent: pin, archive, restore, and permanent delete.
 * Those are all deliberate, low-frequency, online actions — nobody queues an
 * archive for later — and each one added here is another branch in the batch
 * handler and another conflict case to reason about. Kept out until there's a
 * real need.
 *
 * <p>DELETE means SOFT delete, the same trash the online endpoint uses.
 * Permanently destroying data from a replayed queue — possibly minutes or days
 * after the user tapped it, based on state that may have moved on — is not a
 * risk worth taking for an offline convenience.
 */
public enum SyncOperationType {

    /** A note the client created offline. Carries clientId, no noteId yet. */
    CREATE,

    /** An edit to a note the server already knows. Carries noteId and the version it was based on. */
    UPDATE,

    /** Move to trash. Carries noteId. */
    DELETE
}