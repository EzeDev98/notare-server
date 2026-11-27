package com.notare.common.exception;

/** Thrown when a registration fails. */
public class RegistrationException extends RuntimeException {

    public RegistrationException(String message) {
        super(message);
    }
}
