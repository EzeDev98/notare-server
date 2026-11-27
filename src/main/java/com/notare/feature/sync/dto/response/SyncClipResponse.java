package com.notare.feature.sync.dto.response;

import com.notare.feature.transcription.enums.TranscriptionStatus;

/** A voice clip as the sync pull delivers it. */
public record SyncClipResponse(
        Long id,
        Long noteId,
        String clientId,
        Integer durationSeconds,
        Long sizeBytes,
        String mimeType,
        boolean deleted,
        Transcript transcript
) {

    public record Transcript(TranscriptionStatus status, String text, String language) {
    }
}
