package com.notare.feature.transcription.event;

/**
 * A transcription failed. Notify the person who asked for it (step 17d); {@code ownerId} carries that
 * person's id.
 *
 * <p>The message is carried so a consumer can surface something better than
 * "it failed", but it comes straight from an exception and is NOT safe to show
 * verbatim — it can contain provider internals, file paths, or stack detail.
 * The notification stores a type, and 8D deliberately does not put this string
 * in front of the user.
 *
 * <p>Same no-transaction caveat as TranscriptionCompletedEvent.
 */
public record TranscriptionFailedEvent(
        Long transcriptionId,
        Long voiceNoteId,
        Long ownerId,
        String errorMessage
) {
}