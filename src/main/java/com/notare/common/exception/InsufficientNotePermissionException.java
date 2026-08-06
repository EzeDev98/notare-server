package com.notare.common.exception;

/**
 * The caller has a grant on the note but not a strong enough one — a READ
 * recipient attempting a write. Maps to 403 Forbidden.
 *
 * <p>This is the first place in the application where 403 is the RIGHT answer,
 * and the distinction is worth stating precisely, because getting it backwards
 * leaks information.
 *
 * <p>404 (NoteShareNotFoundException / NoteNotFoundException) means "as far as
 * you're concerned this doesn't exist". It's the answer for someone with no
 * relationship to the resource, and the whole point is that it does NOT confirm
 * the note is real — otherwise anyone could probe ids and map out other users'
 * notes from the status codes alone.
 *
 * <p>403 means "this exists, you can see it, but you may not do that". It's only
 * safe once the caller has already been let through the door — here, once a
 * grant has been found. Telling a READ collaborator that a note they can
 * legitimately open exists reveals nothing they didn't already know, and a 404
 * would be actively confusing: they're looking at the note.
 *
 * <p>The rule, then: 404 protects existence from strangers; 403 denies an
 * action to someone already inside.
 */
public class InsufficientNotePermissionException extends RuntimeException {
    public InsufficientNotePermissionException(String message) {
        super(message);
    }
}