package com.notare.common.exception;

public class CollaborationAlreadyExistsException extends RuntimeException {
    public CollaborationAlreadyExistsException(String message) {
        super(message);
    }
}