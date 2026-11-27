package com.notare.feature.transcription.engine.impl;

import com.notare.feature.transcription.dto.response.TranscriptionResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Tests for StubTranscriptionEngine. */
class StubTranscriptionEngineTest {

    private final StubTranscriptionEngine engine = new StubTranscriptionEngine();

    @Test
    void transcribe_returnsPlaceholderTextEmbeddingTheGivenContentType() {
        Resource audio = mock(Resource.class);

        TranscriptionResult result = engine.transcribe(audio, "audio/mp4");

        assertThat(result.transcript()).contains("audio/mp4");
        assertThat(result.transcript()).startsWith("[stub transcript]");
        verifyNoInteractions(audio);
    }

    @Test
    void transcribe_differentContentTypesProduceDifferentPlaceholderText() {
        Resource audio = mock(Resource.class);

        TranscriptionResult mp3Result = engine.transcribe(audio, "audio/mpeg");
        TranscriptionResult wavResult = engine.transcribe(audio, "audio/wav");

        assertThat(mp3Result.transcript()).contains("audio/mpeg").doesNotContain("audio/wav");
        assertThat(wavResult.transcript()).contains("audio/wav").doesNotContain("audio/mpeg");
    }

    @Test
    void transcribe_alwaysReturnsTheFixedConfidenceAndLanguage() {
        Resource audio = mock(Resource.class);

        TranscriptionResult result = engine.transcribe(audio, "audio/mpeg");

        assertThat(result.confidence()).isEqualTo(0.95);
        assertThat(result.language()).isEqualTo("en");
    }

    @Test
    void transcribe_neverReadsFromTheSuppliedResource() {
        Resource audio = mock(Resource.class);

        engine.transcribe(audio, "audio/mpeg");

        verifyNoInteractions(audio);
    }

    @Test
    void transcribe_interruptedDuringTheSimulatedDelay_restoresTheInterruptFlagAndStillReturnsAResult()
            throws InterruptedException {
        Resource audio = mock(Resource.class);
        Thread[] worker = new Thread[1];
        TranscriptionResult[] outcome = new TranscriptionResult[1];

        Thread runner = new Thread(() -> {
            worker[0] = Thread.currentThread();
            outcome[0] = engine.transcribe(audio, "audio/mpeg");
        });
        runner.start();
        Thread.sleep(100);
        runner.interrupt();
        runner.join(3000);

        assertThat(runner.isAlive()).isFalse();
        assertThat(outcome[0]).isNotNull();
        assertThat(outcome[0].transcript()).contains("audio/mpeg");
    }
}
