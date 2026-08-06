package com.notare.common.exception;

public class TranscriptionNotFoundException extends RuntimeException {
    public TranscriptionNotFoundException(String message) {
        super(message);
    }
}