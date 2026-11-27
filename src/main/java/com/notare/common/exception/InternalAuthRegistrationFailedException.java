package com.notare.common.exception;

/** Thrown when internal-auth-server refuses a registration. */
public class InternalAuthRegistrationFailedException extends RuntimeException {

    public InternalAuthRegistrationFailedException(String message) {
        super(message);
    }
}
