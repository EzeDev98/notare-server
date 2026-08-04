package com.notare.common.exception;

public class NoteConflictException extends RuntimeException {

    /**
     * The note's current version on the server. Sent back to the client in the
     * 409 response so it knows the server has moved ahead. We carry a plain Long
     * (not the note DTO) on purpose: this exception lives in the shared `common`
     * package, and having `common` depend on a feature's DTO would invert the
     * module dependency. The client re-fetches the note to reconcile.
     */
    private final Long currentVersion;

    public NoteConflictException(String message, Long currentVersion) {
        super(message);
        this.currentVersion = currentVersion;
    }

    public Long getCurrentVersion() {
        return currentVersion;
    }
}