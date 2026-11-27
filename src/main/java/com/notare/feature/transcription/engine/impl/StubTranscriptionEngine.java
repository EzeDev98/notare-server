package com.notare.feature.transcription.engine.impl;

import com.notare.feature.transcription.dto.response.TranscriptionResult;
import com.notare.feature.transcription.engine.TranscriptionEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Placeholder transcription engine for dev and tests. */
@Component
@ConditionalOnProperty(prefix = "notare.transcription", name = "engine", havingValue = "stub", matchIfMissing = true)
public class StubTranscriptionEngine implements TranscriptionEngine {

    @Override
    public TranscriptionResult transcribe(Resource audio, String contentType) {
        sleepQuietly(1500);

        String placeholder = "[stub transcript] audio (" + contentType + ") transcribed by the development engine.";
        return new TranscriptionResult(placeholder, 0.95, "en");
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
