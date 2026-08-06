package com.notare.feature.share.enums;

/**
 * What a recipient is allowed to do with a note that's been shared with them:
 *   READ  -> open and view it
 *   WRITE -> view it AND edit title/content
 *
 * <p>Deliberately narrow. WRITE does NOT cover delete, pin, or archive: pin and
 * archive are the owner's personal view state on their own note, and delete is
 * destructive and irreversible from a collaborator's side. Widening a permission
 * later is easy; taking one back after clients depend on it is not.
 *
 * <p>Two levels rather than a bitmask or a role table. A fixed enum keeps the
 * rules readable and the column indexable, and this app has no third level in
 * sight. If one ever appears (COMMENT, say), it slots in below.
 */
public enum SharePermission {

    READ,
    WRITE;

    /**
     * Does this granted permission satisfy the one an operation requires?
     *
     * <p>The rule "WRITE implies READ" lives HERE, in one place, rather than as
     * a scattered {@code if (p == WRITE || p == READ)} in every service method.
     * Every access check calls this, so if the ladder ever grows, exactly one
     * method changes.
     *
     * <p>{@code ordinal()} is used only for this in-memory comparison, never for
     * persistence — the column is @Enumerated(STRING), so reordering the
     * constants can't corrupt stored data. It does change this ranking, though,
     * so keep the constants ordered weakest-first.
     */
    public boolean satisfies(SharePermission required) {
        return this.ordinal() >= required.ordinal();
    }
}