package com.notare.common.exception;

import lombok.Getter;

/** Thrown when a per-user request limit is exceeded. */
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super("Too many requests. Try again in " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
