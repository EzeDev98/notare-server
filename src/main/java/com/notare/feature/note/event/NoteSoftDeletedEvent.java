package com.notare.feature.note.event;

/**
 * Published when a note is moved to the trash (step 15e).
 *
 * <p>A trashed note is invisible to everyone, its collaborators included, so
 * anyone editing it live has to be disconnected. Unlike
 * NotePermanentlyDeletedEvent this one can be undone by a restore, and nothing is
 * deleted: it only states that the note just became unreachable.
 */
public record NoteSoftDeletedEvent(Long noteId, Long ownerId) {
}
