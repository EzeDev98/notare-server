package com.notare.feature.transcription.event;

/** Event published when a transcription is requested. */
public record TranscriptionRequestedEvent(Long transcriptionId) {
}
