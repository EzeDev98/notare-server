package com.notare.feature.note.event;

/** Event published when a note is deleted for good. */
public record NotePermanentlyDeletedEvent(Long noteId, Long ownerId) {
}
