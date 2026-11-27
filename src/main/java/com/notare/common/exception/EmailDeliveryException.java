package com.notare.common.exception;

/** Thrown when an email cannot be sent. */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message) {
        super(message);
    }
}
