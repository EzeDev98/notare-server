package com.notare.common.exception;

/** Thrown when a note does not exist or is not the caller's. */
public class NoteNotFoundException extends RuntimeException {
    public NoteNotFoundException(String message) {
        super(message);
    }
}
