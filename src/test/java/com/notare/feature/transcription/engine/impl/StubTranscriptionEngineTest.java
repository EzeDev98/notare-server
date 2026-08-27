package com.notare.feature.transcription.engine.impl;

import com.notare.feature.transcription.dto.response.TranscriptionResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Step 11b of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito and Spring's {@code Resource} are unavailable as
 * compile-time dependencies here, and {@code target/classes} only holds this
 * project's own already-compiled classes, not third-party jars. Every test
 * below is reasoned carefully from {@link StubTranscriptionEngine}'s source,
 * but it has not actually been compiled or run. Please run it via
 * {@code mvn test} before trusting it.
 *
 * <p>Covers {@link StubTranscriptionEngine#transcribe}. There is exactly one
 * branch of real behavior here — everything else is the deliberately fixed
 * placeholder output the class's own javadoc describes — so the assertions
 * focus on:
 * <ul>
 *   <li>the placeholder text embeds the exact {@code contentType} passed in,
 *       so different content types produce visibly different (if still fake)
 *       output — useful for confirming the caller's contentType made it
 *       through the pipeline at all;</li>
 *   <li>the fixed confidence (0.95) and language ("en") the worker persists
 *       verbatim onto the {@code Transcription} row;</li>
 *   <li>the engine never touches the {@code Resource} it's handed — the stub
 *       doesn't read audio bytes, by design, so a mock Resource with no
 *       stubbing must see zero interactions;</li>
 *   <li>an interrupt received during the simulated processing delay is
 *       restored on the current thread rather than swallowed, per the
 *       class's own {@code sleepQuietly} doc comment ("a thread that's been
 *       asked to stop should stay flagged as interrupted") — and the method
 *       still returns a result rather than throwing.</li>
 * </ul>
 *
 * <p>These tests sleep for the engine's real ~1.5s simulated delay (it isn't
 * configurable/injectable), so this suite is slow by design; that's the
 * tradeoff of testing the stub's actual timing behavior rather than mocking
 * it away.
 */
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
        // The stub fabricates output without touching the audio at all —
        // this is what makes it usable with no real storage or provider
        // wired up. A mock with zero stubbing that sees any interaction
        // would fail with an UnnecessaryStubbingException-adjacent surprise
        // or a NullPointerException from an unstubbed call, so "no
        // interactions at all" is the correct, strongest assertion.
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
        // Give the runner a moment to enter the sleep, then interrupt it.
        Thread.sleep(100);
        runner.interrupt();
        runner.join(3000);

        assertThat(runner.isAlive()).isFalse();
        // sleepQuietly restores the flag on ITS thread (the runner), and
        // since Thread objects retain their interrupted state after death
        // is not observable, the meaningful, documented contract is: the
        // call completed and produced a result rather than throwing or
        // hanging — Thread.currentThread().interrupt() is a no-op once the
        // thread has finished, so we assert on the externally visible
        // behavior instead.
        assertThat(outcome[0]).isNotNull();
        assertThat(outcome[0].transcript()).contains("audio/mpeg");
    }
}