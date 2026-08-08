package com.notare.common.exception;

/**
 * No such notification, or it isn't the caller's. One exception for both, and
 * therefore one 404 — same existence-hiding rule the rest of the project
 * follows. There is no 403 here: unlike a shared note, a notification never
 * has a caller who is legitimately "inside the door" but under-permissioned.
 */
public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(String message) {
        super(message);
    }
}