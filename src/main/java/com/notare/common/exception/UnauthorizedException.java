package com.notare.common.exception;

import lombok.Getter;

/** Thrown when a caller is not authorised. */
@Getter
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
