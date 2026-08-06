package com.notare.feature.share.event;

/**
 * Published inside the permanent-delete transaction, before the note row goes.
 *
 * <p>This event exists to keep the module dependency pointing one way. The
 * share module owns the grants that reference a note, and it already depends on
 * the note module — so the note module cannot simply call the share module to
 * clean them up without creating a cycle. Publishing a fact ("this note is
 * gone") lets the interested module react without the publisher knowing it
 * exists.
 *
 * <p>Deliberately NOT published on soft delete. A trashed note can be restored,
 * and its grants should survive that round trip; the note is already invisible
 * to collaborators in the meantime because every shared-note query filters on
 * deleted = false.
 */
public record NotePermanentlyDeletedEvent(Long noteId) {
}