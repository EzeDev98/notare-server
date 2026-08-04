package com.notare.common.exception;

import org.yaml.snakeyaml.reader.StreamReader;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
