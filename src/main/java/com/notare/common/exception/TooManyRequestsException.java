package com.notare.common.exception;

import lombok.Getter;

/** A per-user request limit was exceeded (step 17j, B-15). Answered as 429 with Retry-After. */
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super("Too many requests. Try again in " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
