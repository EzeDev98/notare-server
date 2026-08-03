package com.notare.common.exception;

public class OtpResendCooldownException extends RuntimeException {
    public OtpResendCooldownException(String message) {
        super(message);
    }
}
