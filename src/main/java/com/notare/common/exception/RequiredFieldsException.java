package com.notare.common.exception;

/** Thrown when required fields are missing. */
public class RequiredFieldsException extends RuntimeException {

    public RequiredFieldsException(String message) {
        super(message);
    }
}
