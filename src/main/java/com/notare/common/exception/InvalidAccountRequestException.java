package com.notare.common.exception;

/** An account request that cannot be carried out as asked, such as a confirmation that does not match (step 17g). Answers 400. */
public class InvalidAccountRequestException extends RuntimeException {
    public InvalidAccountRequestException(String message) {
        super(message);
    }
}
