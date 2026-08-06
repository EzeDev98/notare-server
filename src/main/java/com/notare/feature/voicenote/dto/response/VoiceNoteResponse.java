package com.notare.feature.voicenote.dto.response;

import java.time.LocalDateTime;

/**
 * Metadata view of a voice note. Note what's absent: storageKey. That's an
 * internal detail of where bytes live and is never exposed to clients — they
 * reference a voice note by its id, and download/stream endpoints resolve the
 * key server-side. createdAt doubles as the upload date.
 */
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