package com.notare.feature.voicenote.event;

/**
 * A voice note was successfully persisted.
 *
 * <p>The event deliberately lives in the voicenote module. The voicenote
 * module announces the fact that a recording now exists; consumers such as
 * the sharing module decide whether that recording needs additional access
 * grants.
 *
 * <p>This is intentionally separate from {@link VoiceNoteDeletedEvent}:
 * creation and deletion have different consumers and different lifecycle
 * semantics.
 */
public record VoiceNoteCreatedEvent(
        Long voiceNoteId,
        Long ownerId,
        Long noteId
) {
}
