package com.notare.common.exception;

/** Thrown when a one-time code has expired. */
public class OtpExpiredException extends RuntimeException {
    public OtpExpiredException(String message) {
        super(message);
    }
}
