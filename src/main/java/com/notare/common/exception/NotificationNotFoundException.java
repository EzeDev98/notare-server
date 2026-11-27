package com.notare.common.exception;

/** Thrown when a notification does not exist or is not the caller's. */
public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(String message) {
        super(message);
    }
}
