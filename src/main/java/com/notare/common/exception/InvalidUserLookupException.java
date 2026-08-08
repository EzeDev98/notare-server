package com.notare.common.exception;

/**
 * A user-lookup request the server will not serve as asked: no ids, or more
 * than the per-request ceiling.
 *
 * <p>Its own exception rather than a reused one so the 400 says something
 * specific. Follows the module's existing naming — InvalidCollaborationException,
 * InvalidNoteShareException, InvalidVoiceUploadException — all of which mean
 * "structurally wrong request" and all of which map to 400.
 */
public class InvalidUserLookupException extends RuntimeException {
    public InvalidUserLookupException(String message) {
        super(message);
    }
}