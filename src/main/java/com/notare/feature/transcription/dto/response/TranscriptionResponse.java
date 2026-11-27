package com.notare.feature.transcription.dto.response;

import com.notare.feature.transcription.enums.TranscriptionStatus;

import java.time.LocalDateTime;

/** A transcription's status and result. */
public record TranscriptionResponse(
        Long id,
        Long voiceNoteId,
        TranscriptionStatus status,
        String transcript,
        Double confidence,
        String language,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
