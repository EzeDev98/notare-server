package com.notare.common.exception;

public class InvalidVoiceUploadException extends RuntimeException {
    public InvalidVoiceUploadException(String message) {
        super(message);
    }
}