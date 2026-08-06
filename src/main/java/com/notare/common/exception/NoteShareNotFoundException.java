package com.notare.common.exception;

/**
 * No grant exists for the requested (note, user) pair, or the caller isn't entitled to see it. -> 404, which also hides the note's existence from a stranger.
 */
public class NoteShareNotFoundException extends RuntimeException {
    public NoteShareNotFoundException(String message) {
        super(message);
    }
}