package com.notare.common.exception;

/** Thrown when a voice file is too large. */
public class VoiceFileTooLargeException extends RuntimeException {
    public VoiceFileTooLargeException(String message) {
        super(message);
    }
}
