package com.notare.common.exception;

/**
 * Thrown when a write carries a version that is not the note's current one (step 17a, D-17).
 *
 * <p>Layer 1 of the two optimistic-locking layers: it catches the ordinary case, a client editing
 * from stale state, with a clean 409 that names the current version. Layer 2 is Hibernate's own
 * {@code @Version} check, for two writes that race past this one.
 */
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
