package com.notare.common.exception;

public class RefreshTokenInvalidException extends RuntimeException {
    public RefreshTokenInvalidException(String message) {
        super(message);
    }

    public RefreshTokenInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}