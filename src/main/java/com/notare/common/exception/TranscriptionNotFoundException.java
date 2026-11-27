package com.notare.common.exception;

/** Thrown when a transcription does not exist or is not the caller's. */
public class TranscriptionNotFoundException extends RuntimeException {
    public TranscriptionNotFoundException(String message) {
        super(message);
    }
}
