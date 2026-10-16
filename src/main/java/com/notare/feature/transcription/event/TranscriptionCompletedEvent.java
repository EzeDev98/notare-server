package com.notare.feature.transcription.event;

/**
 * A transcription finished successfully. Notify the person who asked for it, the clip's owner or
 * an Editor (step 17d); {@code ownerId} carries that person's id. The notification has no actor.
 *
 * <p>Published from the async worker, NOT from a request thread. That has a
 * consequence for consumers: there is no surrounding transaction, so a plain
 * @TransactionalEventListener(AFTER_COMMIT) would silently never fire. See the
 * worker's javadoc — the listener in 8D sets fallbackExecution = true.
 */
public record TranscriptionCompletedEvent(
        Long transcriptionId,
        Long voiceNoteId,
        Long ownerId
) {
}