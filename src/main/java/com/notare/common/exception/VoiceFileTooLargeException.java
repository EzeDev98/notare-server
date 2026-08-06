package com.notare.common.exception;

public class VoiceFileTooLargeException extends RuntimeException {
    public VoiceFileTooLargeException(String message) {
        super(message);
    }
}