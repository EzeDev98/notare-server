package com.notare.common.exception;

/** Thrown when a refresh token is rejected. */
public class RefreshTokenInvalidException extends RuntimeException {
    public RefreshTokenInvalidException(String message) {
        super(message);
    }

    public RefreshTokenInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
