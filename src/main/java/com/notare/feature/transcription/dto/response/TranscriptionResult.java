package com.notare.feature.transcription.dto.response;

/**
 * What an engine returns on success: the text plus the metadata the spec asks us
 * to store (confidence 0.0–1.0, detected language). These map straight onto the
 * Transcription entity's fields when a job completes.
 */
public record TranscriptionResult(
        String transcript,
        Double confidence,
        String language
) {
}