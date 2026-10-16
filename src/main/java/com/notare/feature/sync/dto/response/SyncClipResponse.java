package com.notare.feature.sync.dto.response;

import com.notare.feature.transcription.enums.TranscriptionStatus;

/**
 * A voice clip as the pull delivers it (step 17c): enough for another device to show the clip, fetch
 * its audio, show its transcript, or drop it when {@code deleted} is true. No storage key, ever.
 */
public record SyncClipResponse(
        Long id,
        Long noteId,
        /** The id the recording device gave the clip, so it recognises its own clip coming back. */
        String clientId,
        Integer durationSeconds,
        Long sizeBytes,
        String mimeType,
        /** Hidden: its note is in the trash, or it was deleted on its own. Hard deletes come as note tombstones. */
        boolean deleted,
        /** Null when the clip has no transcription. */
        Transcript transcript
) {

    /** The transcript's state. {@code text} and {@code language} are null until it is COMPLETED. */
    public record Transcript(TranscriptionStatus status, String text, String language) {
    }
}
