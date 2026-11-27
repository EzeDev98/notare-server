package com.notare.common.exception;

import lombok.Getter;

/** Thrown when a client is older than the minimum supported version. */
@Getter
public class ClientTooOldException extends RuntimeException {

    private final String minimumVersion;

    public ClientTooOldException(String minimumVersion) {
        super("This version of Notare is too old. Reload to update.");
        this.minimumVersion = minimumVersion;
    }
}
