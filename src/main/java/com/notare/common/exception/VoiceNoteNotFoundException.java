package com.notare.common.exception;

public class VoiceNoteNotFoundException extends RuntimeException {
    public VoiceNoteNotFoundException(String message) {
        super(message);
    }
}