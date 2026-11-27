package com.notare.common.exception;

/** Thrown when a one-time code is wrong. */
public class InvalidOtpException extends RuntimeException {
    public InvalidOtpException(String message) {
        super(message);
    }
}
