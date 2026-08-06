package com.notare.feature.transcription.engine.impl;

import com.notare.feature.transcription.dto.response.TranscriptionResult;
import com.notare.feature.transcription.engine.TranscriptionEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Development/test engine. It doesn't call any real speech-to-text service — it
 * simulates a short processing delay and returns placeholder text, so the ENTIRE
 * async pipeline (submit -> PROCESSING -> COMPLETED, status polling) works and
 * can be learned end-to-end without an API key or per-minute cost.
 *
 * @Profile("!prod") mirrors the storage backend: this stub is active everywhere
 * except prod; a real @Profile("prod") engine is a later drop-in.
 */
@Component
@Profile("!prod")
public class StubTranscriptionEngine implements TranscriptionEngine {

    @Override
    public TranscriptionResult transcribe(Resource audio, String contentType) {
        // Simulate work so the job visibly sits in PROCESSING for a moment
        // before flipping to COMPLETED — makes the async flow observable.
        sleepQuietly(1500);

        String placeholder = "[stub transcript] audio (" + contentType + ") transcribed by the development engine.";
        return new TranscriptionResult(placeholder, 0.95, "en");
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Restore the interrupt flag rather than swallowing it — a thread
            // that's been asked to stop should stay flagged as interrupted.
            Thread.currentThread().interrupt();
        }
    }
}