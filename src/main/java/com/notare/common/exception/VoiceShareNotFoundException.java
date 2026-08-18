package com.notare.common.exception;

/**
 * No grant exists for the requested (voice note, user) pair, or the caller isn't entitled to see it. -> 404, which also hides the voice note's existence from a stranger.
 */
public class VoiceShareNotFoundException extends RuntimeException {
    public VoiceShareNotFoundException(String message) {
        super(message);
    }
}
