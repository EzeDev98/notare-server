package com.notare.feature.voicenote.dto.response;

/** One voice clip as the sync pull needs it. */
public record ClipChange(
        Long id,
        Long noteId,
        String clientId,
        Integer durationSeconds,
        Long sizeBytes,
        String contentType,
        boolean deleted
) {
}
