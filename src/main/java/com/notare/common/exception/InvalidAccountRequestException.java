package com.notare.common.exception;

/** Thrown when an account request cannot be carried out as asked. */
public class InvalidAccountRequestException extends RuntimeException {
    public InvalidAccountRequestException(String message) {
        super(message);
    }
}
