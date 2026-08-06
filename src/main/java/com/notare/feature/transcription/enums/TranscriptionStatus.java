package com.notare.feature.transcription.enums;

/**
 * Lifecycle of a transcription job:
 *   PENDING    -> just submitted, not yet picked up
 *   PROCESSING -> the async worker is running it
 *   COMPLETED  -> transcript/confidence/language are populated
 *   FAILED     -> errorMessage explains why
 */
public enum TranscriptionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}