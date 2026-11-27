package com.notare.feature.voicenote.dto.response;

import java.time.LocalDateTime;

/** A voice clip's details, without its audio. */
public record VoiceNoteResponse(
        Long id,
        Long ownerId,
        Long noteId,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        Integer durationSeconds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
