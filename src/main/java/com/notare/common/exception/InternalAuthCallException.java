package com.notare.common.exception;

import org.springframework.http.HttpStatus;

/** Thrown when internal-auth-server returns an unexpected error. */
public class InternalAuthCallException extends RuntimeException {

    private final HttpStatus status;

    public InternalAuthCallException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
