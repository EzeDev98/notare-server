package com.notare.feature.transcription.dto.response;

/** What a transcription engine returns: text, confidence and language. */
public record TranscriptionResult(
        String transcript,
        Double confidence,
        String language
) {
}
