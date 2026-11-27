package com.notare.common.exception;

/** Thrown when a unique Notarian id cannot be generated. */
public class NotarianIdGenerationException extends RuntimeException {
    public NotarianIdGenerationException(String message) {
        super(message);
    }
}
