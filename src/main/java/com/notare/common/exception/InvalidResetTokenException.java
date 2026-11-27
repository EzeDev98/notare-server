package com.notare.common.exception;

/** Thrown when a password-reset token is not valid. */
public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException(String message) {
        super(message);
    }
}
