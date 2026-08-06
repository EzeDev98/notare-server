package com.notare.common.exception;

/**
 * The share request is structurally wrong: sharing with yourself, or with someone who isn't an accepted collaborator. -> 400.
 */
public class InvalidNoteShareException extends RuntimeException {
    public InvalidNoteShareException(String message) {
        super(message);
    }
}