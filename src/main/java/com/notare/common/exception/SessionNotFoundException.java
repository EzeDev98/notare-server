package com.notare.common.exception;

/** Thrown when a session does not exist. */
public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String message) {
        super(message);
    }
}
