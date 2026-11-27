package com.notare.common.exception;

import lombok.Getter;

/** Thrown when an account has not been verified. */
@Getter
public class AccountNotVerifiedException extends RuntimeException {
    public AccountNotVerifiedException(String message) {
        super(message);
    }
}
