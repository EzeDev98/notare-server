package com.notare.common.exception;

/** Thrown when a user does not exist. */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
