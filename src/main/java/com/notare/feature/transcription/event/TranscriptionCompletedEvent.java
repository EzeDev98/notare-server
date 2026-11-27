package com.notare.feature.transcription.event;

/** Event published when a transcription finishes. */
public record TranscriptionCompletedEvent(
        Long transcriptionId,
        Long voiceNoteId,
        Long ownerId
) {
}
