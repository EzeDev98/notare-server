package com.notare.common.exception;

/** Thrown when a one-time code is asked for again too soon. */
public class OtpResendCooldownException extends RuntimeException {
    public OtpResendCooldownException(String message) {
        super(message);
    }
}
