package com.notare.feature.note.event;

/**
 * Published inside the permanent-delete transaction, before the note row goes.
 *
 * <p>This event exists to keep the module dependency pointing one way. Other
 * modules own rows that reference a note, and they already depend on the note
 * module — so the note module cannot call them to clean up without creating a
 * cycle. Publishing a fact ("this note is gone") lets interested modules react
 * without the publisher knowing they exist. Two currently listen: the share
 * module drops the note's grants (7F), and the sync module records a tombstone
 * so offline clients learn the note is gone (9F).
 *
 * <p>ownerId is carried as well as noteId. A consumer that only got the id
 * would have to look the note up to find its owner — and by the time any
 * listener runs, that row is being deleted. An event has to be a complete
 * statement of what happened precisely because the world it describes may no
 * longer exist.
 *
 * <p>Deliberately NOT published on soft delete. A trashed note can be restored,
 * and it already reaches offline clients as a tombstone through the ordinary
 * delta query, because the row is still there.
 */
public record NotePermanentlyDeletedEvent(Long noteId, Long ownerId) {
}