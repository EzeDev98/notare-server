package com.notare.feature.user.enums;

/** How far an account deletion has got (step 17g). Each step is done and recorded before the next. */
public enum AccountDeletionStatus {
    /** Recorded; nothing deleted yet. */
    STARTED,
    /** Every module has deleted what it owns. */
    DATA_DELETED,
    /** What the deletes left behind (tombstones, notifications, authorship) is cleared. */
    SWEPT,
    /** The auth user is gone. Only the local user row remains. */
    AUTH_DELETED
}
