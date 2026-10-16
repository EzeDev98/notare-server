package com.notare.feature.share.enums;

/**
 * What one person may do with a note, as the words the live editing session already uses (step 17d).
 * It answers "may this user touch this note's clips?" for modules that cannot see shares themselves.
 */
public enum NoteAccess {

    /** The note's owner. */
    OWNER,

    /** Someone with WRITE on the note. */
    EDITOR,

    /** Someone with READ only. */
    VIEWER;

    /** Owners and Editors may change the note and its clips; Viewers may only look. */
    public boolean canWrite() {
        return this != VIEWER;
    }
}
