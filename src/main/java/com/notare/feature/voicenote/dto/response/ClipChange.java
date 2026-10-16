package com.notare.feature.voicenote.dto.response;

/**
 * One clip as the sync pull needs it (step 17c): what the other devices must know to show it and
 * to fetch its audio, and whether it is gone. Not VoiceNoteResponse: that describes a visible clip
 * and has no way to say "deleted".
 */
public record ClipChange(
        Long id,
        Long noteId,
        /** The clip's id on the device that recorded it, or null for a clip uploaded the old way. */
        String clientId,
        Integer durationSeconds,
        Long sizeBytes,
        String contentType,
        /** Hidden: its note is in the trash, or it was deleted on its own. */
        boolean deleted
) {
}
