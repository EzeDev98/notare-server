package com.notare.common.exception;

/** Thrown when two passwords do not match. */
public class PasswordMisMatchException extends RuntimeException {

    public PasswordMisMatchException(String message) {
        super(message);
    }
}
