package com.notare.feature.transcription.dto.response;

import com.notare.feature.transcription.enums.TranscriptionStatus;

import java.time.LocalDateTime;

/**
 * What the client gets on submit and on every status poll. status serializes as
 * its name ("PENDING", "COMPLETED", …). transcript/confidence/language are null
 * until the job COMPLETES; errorMessage is null unless it FAILED. So the client
 * reads `status` first and only expects the other fields once it's COMPLETED.
 */
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