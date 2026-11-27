package com.notare.common.exception;

/** Thrown when an audio format is not supported. */
public class UnsupportedAudioFormatException extends RuntimeException {
    public UnsupportedAudioFormatException(String message) {
        super(message);
    }
}
