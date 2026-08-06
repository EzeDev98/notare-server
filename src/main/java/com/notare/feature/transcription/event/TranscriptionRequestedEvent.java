package com.notare.feature.transcription.event;

/**
 * Published inside the submit transaction, right after the PENDING job row is
 * saved. A listener consumes it in the AFTER_COMMIT phase so the async worker
 * never queries for the row before the transaction that created it has
 * committed and made it visible — closing the race we'd hit if we started the
 * worker inline.
 */
public record TranscriptionRequestedEvent(Long transcriptionId) {
}