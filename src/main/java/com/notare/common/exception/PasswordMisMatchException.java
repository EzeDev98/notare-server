package com.notare.common.exception;

public class PasswordMisMatchException extends RuntimeException {

    public PasswordMisMatchException(String message) {
        super(message);
    }
}
