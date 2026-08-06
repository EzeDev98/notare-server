package com.notare.common.exception;

public class CollaborationNotFoundException extends RuntimeException {
    public CollaborationNotFoundException(String message) {
        super(message);
    }
}