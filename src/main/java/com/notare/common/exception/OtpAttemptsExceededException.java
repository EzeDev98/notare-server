package com.notare.common.exception;

/** Thrown when too many wrong one-time codes were entered. */
public class OtpAttemptsExceededException extends RuntimeException {
    public OtpAttemptsExceededException(String message) {
        super(message);
    }
}
