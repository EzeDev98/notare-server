package com.notare.feature.voicenote.event;

/** Event published when a voice clip is deleted for good. */
public record VoiceNoteDeletedEvent(
        Long voiceNoteId,
        String voiceNoteTitle,
        Long ownerId
) {
}
