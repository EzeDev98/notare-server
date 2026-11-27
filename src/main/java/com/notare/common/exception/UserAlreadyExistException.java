package com.notare.common.exception;

/** Thrown when a user already exists. */
public class UserAlreadyExistException extends RuntimeException {
    public UserAlreadyExistException(String message) {
        super(message);
    }
}
