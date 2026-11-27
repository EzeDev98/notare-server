package com.notare.common.exception;

/** Thrown when a voice clip does not exist or is not the caller's. */
public class VoiceNoteNotFoundException extends RuntimeException {
    public VoiceNoteNotFoundException(String message) {
        super(message);
    }
}
