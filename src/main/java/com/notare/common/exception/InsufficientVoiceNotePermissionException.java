package com.notare.common.exception;

/**
 * The caller has a grant on the voice note but not a strong enough one — a
 * READ recipient attempting a write. Maps to 403 Forbidden.
 *
 * <p>Mirrors {@link InsufficientNotePermissionException}'s reasoning exactly:
 * 404 (VoiceShareNotFoundException / voice-note-not-found) protects existence
 * from strangers; 403 denies an action to someone already let through the
 * door, i.e. someone who already holds a grant.
 */
public class InsufficientVoiceNotePermissionException extends RuntimeException {
    public InsufficientVoiceNotePermissionException(String message) {
        super(message);
    }
}
