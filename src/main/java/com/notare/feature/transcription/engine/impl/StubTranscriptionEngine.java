package com.notare.feature.transcription.engine.impl;

import com.notare.feature.transcription.dto.response.TranscriptionResult;
import com.notare.feature.transcription.engine.TranscriptionEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * It doesn't call any real speech-to-text service — it simulates a short
 * processing delay and returns placeholder text, so the ENTIRE async
 * pipeline (submit -> PROCESSING -> COMPLETED, status polling) works and
 * can be learned end-to-end without an API key or per-minute cost.
 *
 * TEMPORARILY active in every profile, including prod: no real
 * TranscriptionEngine implementation exists yet, and TranscriptionWorker's
 * constructor requires SOME bean of this type to be present or the whole
 * app fails to boot (this is what took prod down — zero qualifying beans
 * under the old @Profile("!prod")). Same "stand-in until the real thing is
 * built" reasoning as PostgresFileStorageService for storage, except here
 * there's no second implementation yet to gate behind a flag — until one
 * exists, prod voice notes will get real audio storage but only
 * PLACEHOLDER transcripts. Swap back to @Profile("!prod") the moment a
 * real engine (e.g. Whisper API) lands, so this stub stops being reachable
 * in prod by accident.
 */
@Component
@ConditionalOnProperty(prefix = "notare.transcription", name = "engine", havingValue = "stub", matchIfMissing = true)
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