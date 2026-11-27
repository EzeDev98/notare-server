package com.notare.common.exception;

/** Thrown when a write is based on an old version of a note. */
public class NoteConflictException extends RuntimeException {

    private final Long currentVersion;

    public NoteConflictException(Long noteId, Long currentVersion) {
        super("Note " + noteId + " has been modified since you loaded it.");
        this.currentVersion = currentVersion;
    }

    public Long getCurrentVersion() {
        return currentVersion;
    }
}
