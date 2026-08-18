package com.notare.common.exception;

/**
 * The voice note is already shared with this user. -> 409. The unique constraint uq_voice_shares_voice_note_user is the real guard; this is the friendly pre-check and the race-loser translation.
 */
public class VoiceAlreadySharedException extends RuntimeException {
    public VoiceAlreadySharedException(String message) {
        super(message);
    }
}
