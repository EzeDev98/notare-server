package com.notare.common.exception;

import org.yaml.snakeyaml.reader.StreamReader;

/** Thrown when a refresh token is not valid. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
