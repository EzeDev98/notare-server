package com.notare.common.exception;

/**
 * The note is already shared with this user. -> 409. The unique constraint uq_note_shares_note_user is the real guard; this is the friendly pre-check and the race-loser translation.
 */
public class NoteAlreadySharedException extends RuntimeException {
    public NoteAlreadySharedException(String message) {
        super(message);
    }
}