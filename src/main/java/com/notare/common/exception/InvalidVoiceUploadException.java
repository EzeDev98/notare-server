package com.notare.common.exception;

/** Thrown when a voice upload is not valid. */
public class InvalidVoiceUploadException extends RuntimeException {
    public InvalidVoiceUploadException(String message) {
        super(message);
    }
}
