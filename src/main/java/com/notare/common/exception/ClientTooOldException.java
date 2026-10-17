package com.notare.common.exception;

import lombok.Getter;

/** The client's version is below the configured minimum (step 17k). Answered as 426 CLIENT_TOO_OLD. */
@Getter
public class ClientTooOldException extends RuntimeException {

    private final String minimumVersion;

    public ClientTooOldException(String minimumVersion) {
        super("This version of Notare is too old. Reload to update.");
        this.minimumVersion = minimumVersion;
    }
}
