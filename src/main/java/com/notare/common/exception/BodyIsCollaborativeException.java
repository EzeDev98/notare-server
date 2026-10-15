package com.notare.common.exception;

/**
 * Thrown when a request tries to write a note's text through the ordinary REST or
 * sync path after the note has become collaborative (step 15f, D-032).
 *
 * <p>Once a note is COLLAB its body is the merged Yjs document. A plain
 * "content = this string" write from an old client or from the legacy sync queue
 * would replace what several people have typed. Refusing it is what keeps that
 * from happening quietly.
 */
public class BodyIsCollaborativeException extends RuntimeException {

    public BodyIsCollaborativeException(Long noteId) {
        super("Note " + noteId + " is edited live; its text can only change through the collaboration channel.");
    }
}
