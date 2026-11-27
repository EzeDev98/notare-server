package com.notare.feature.transcription.event;

/** Event published when a transcription fails. */
public record TranscriptionFailedEvent(
        Long transcriptionId,
        Long voiceNoteId,
        Long ownerId,
        String errorMessage
) {
}
