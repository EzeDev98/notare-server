package com.notare.feature.note.event;

/**
 * A note came back from the trash (step 17b). Anything that was hidden along with it, such as its
 * voice clips, comes back too. The counterpart of {@link NoteSoftDeletedEvent}.
 */
public record NoteRestoredEvent(Long noteId, Long ownerId) {
}
