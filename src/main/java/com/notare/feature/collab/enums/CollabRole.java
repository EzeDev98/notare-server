package com.notare.feature.collab.enums;

/**
 * What a person may do in a live editing session (step 15c, D-032).
 *
 * <p>Derived from who they are to the note: the owner, a recipient with a WRITE
 * grant (EDITOR), or a recipient with a READ grant (VIEWER). The wire form is
 * lowercase, the same words the client already uses for a note's role.
 */
public enum CollabRole {

    OWNER,
    EDITOR,
    VIEWER;

    /** Owners and editors may send document updates; viewers only watch. */
    public boolean canWrite() {
        return this != VIEWER;
    }

    /** The word the client sees: "owner", "editor" or "viewer". */
    public String wire() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
