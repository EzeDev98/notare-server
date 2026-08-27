package com.notare.feature.transcription.worker;

import com.notare.feature.transcription.dto.response.TranscriptionResult;
import com.notare.feature.transcription.engine.TranscriptionEngine;
import com.notare.feature.transcription.enums.TranscriptionStatus;
import com.notare.feature.transcription.event.TranscriptionCompletedEvent;
import com.notare.feature.transcription.event.TranscriptionFailedEvent;
import com.notare.feature.transcription.event.TranscriptionRequestedEvent;
import com.notare.feature.transcription.model.Transcription;
import com.notare.feature.transcription.repository.TranscriptionRepository;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.service.VoiceNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Step 11c of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito and Spring's context/event/async types
 * ({@code ApplicationEventPublisher}, {@code Resource}) are unavailable as
 * compile-time dependencies here, and {@code target/classes} only holds this
 * project's own already-compiled classes, not third-party jars. Every test
 * below is reasoned carefully from {@link TranscriptionWorker}'s source and
 * follows this codebase's existing Mockito conventions exactly, but it has
 * not actually been compiled or run. Please run it via {@code mvn test}
 * before trusting it.
 *
 * <p>These tests call {@link TranscriptionWorker#onTranscriptionRequested}
 * directly, bypassing the {@code @Async}/{@code @TransactionalEventListener}
 * proxying Spring would normally apply — exactly like this codebase's other
 * event-listener suites (see {@code NoteShareCleanupListenerTest}). That
 * proxying is infrastructure wiring, not business logic; what's tested here
 * is what {@code process} actually does once invoked, run synchronously on
 * the test thread so assertions can happen immediately after the call
 * returns rather than needing to await an async completion.
 *
 * <p>Covers, per the method's own doc comments:
 * <ul>
 *   <li>the missing-row short-circuit ("row was deleted between submit and
 *       now — nothing to do"): zero interactions with any other collaborator;</li>
 *   <li>the happy path's two-write shape — PROCESSING saved BEFORE the slow
 *       engine call, COMPLETED saved after — and that the completed event is
 *       published only after the second save, carrying the transcription's
 *       own id/voiceNoteId/ownerId (not the raw event's, confirming the
 *       worker re-reads them off the persisted entity);</li>
 *   <li>that a failure from EITHER the audio load or the engine call is
 *       caught (not propagated) and routed through {@code markFailed}, which
 *       re-reads the row fresh (a separate {@code findById} call, per the
 *       "in-memory entity may be in a partial state" doc comment) rather than
 *       reusing the in-memory reference from earlier in {@code process};</li>
 *   <li>{@code markFailed}'s own missing-row guard: if the row was deleted
 *       between the initial read and the failure, no event is published and
 *       nothing is saved.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TranscriptionWorkerTest {

    private static final Long TRANSCRIPTION_ID = 500L;
    private static final Long VOICE_NOTE_ID = 100L;
    private static final Long OWNER_ID = 1L;

    @Mock
    private TranscriptionRepository transcriptionRepository;

    @Mock
    private VoiceNoteService voiceNoteService;

    @Mock
    private TranscriptionEngine transcriptionEngine;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TranscriptionWorker worker;

    @BeforeEach
    void setUp() {
        worker = new TranscriptionWorker(transcriptionRepository, voiceNoteService, transcriptionEngine, eventPublisher);
    }

    private Transcription pendingRow() {
        Transcription t = new Transcription();
        t.setId(TRANSCRIPTION_ID);
        t.setOwnerId(OWNER_ID);
        t.setVoiceNoteId(VOICE_NOTE_ID);
        t.setStatus(TranscriptionStatus.PENDING);
        return t;
    }

    @Test
    void onTranscriptionRequested_rowNoLongerExists_doesNothing() {
        when(transcriptionRepository.findById(TRANSCRIPTION_ID)).thenReturn(Optional.empty());

        worker.onTranscriptionRequested(new TranscriptionRequestedEvent(TRANSCRIPTION_ID));

        verify(transcriptionRepository, never()).save(any());
        verifyNoInteractions(voiceNoteService, transcriptionEngine, eventPublisher);
    }

    @Test
    void onTranscriptionRequested_happyPath_flipsToProcessingBeforeTheEngineCall_thenCompletedAfter() {
        Transcription row = pendingRow();
        when(transcriptionRepository.findById(TRANSCRIPTION_ID)).thenReturn(Optional.of(row));

        Resource resource = mock(Resource.class);
        VoiceNoteContent audio = new VoiceNoteContent(resource, "audio/mp4", "memo.m4a", 2048L);
        when(voiceNoteService.loadAudio(OWNER_ID, VOICE_NOTE_ID)).thenReturn(audio);

        TranscriptionResult engineResult = new TranscriptionResult("hello world", 0.95, "en");
        when(transcriptionEngine.transcribe(resource, "audio/mp4")).thenReturn(engineResult);

        when(transcriptionRepository.save(row)).thenReturn(row);

        worker.onTranscriptionRequested(new TranscriptionRequestedEvent(TRANSCRIPTION_ID));

        assertThat(row.getStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(row.getTranscript()).isEqualTo("hello world");
        assertThat(row.getConfidence()).isEqualTo(0.95);
        assertThat(row.getLanguage()).isEqualTo("en");
        assertThat(row.getErrorMessage()).isNull();

        // Two distinct writes, and the engine call must happen strictly
        // between them: PROCESSING is visible to a polling client before the
        // slow part starts, and COMPLETED only lands after it finishes.
        InOrder order = inOrder(transcriptionRepository, transcriptionEngine);
        order.verify(transcriptionRepository).save(row); // first save -> PROCESSING at time of call
        order.verify(transcriptionEngine).transcribe(resource, "audio/mp4");
        order.verify(transcriptionRepository).save(row); // second save -> COMPLETED at time of call
        verify(transcriptionRepository, times(2)).save(row);

        verify(eventPublisher).publishEvent(
                new TranscriptionCompletedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID));
        verify(transcriptionRepository, times(1)).findById(TRANSCRIPTION_ID); // no second read needed on the happy path
    }

    @Test
    void onTranscriptionRequested_audioLoadFails_marksFailedWithTheExceptionMessageAndPublishesFailedEvent() {
        Transcription row = pendingRow();
        // First read (process) and second read (markFailed) — a fresh
        // findById per the method's own "reload fresh" doc comment.
        when(transcriptionRepository.findById(TRANSCRIPTION_ID)).thenReturn(Optional.of(row));
        when(transcriptionRepository.save(row)).thenReturn(row);

        RuntimeException loadFailure = new RuntimeException("voice note not found");
        when(voiceNoteService.loadAudio(OWNER_ID, VOICE_NOTE_ID)).thenThrow(loadFailure);

        worker.onTranscriptionRequested(new TranscriptionRequestedEvent(TRANSCRIPTION_ID));

        assertThat(row.getStatus()).isEqualTo(TranscriptionStatus.FAILED);
        assertThat(row.getErrorMessage()).isEqualTo("voice note not found");

        verifyNoInteractions(transcriptionEngine);
        verify(transcriptionRepository, times(2)).findById(TRANSCRIPTION_ID);
        verify(eventPublisher).publishEvent(
                new TranscriptionFailedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID, "voice note not found"));
        // The PROCESSING save (attempted before the failure) plus the FAILED
        // save in markFailed.
        verify(transcriptionRepository, times(2)).save(row);
    }

    @Test
    void onTranscriptionRequested_engineThrows_marksFailedWithoutCompletingTheRow() {
        Transcription row = pendingRow();
        when(transcriptionRepository.findById(TRANSCRIPTION_ID)).thenReturn(Optional.of(row));
        when(transcriptionRepository.save(row)).thenReturn(row);

        Resource resource = mock(Resource.class);
        VoiceNoteContent audio = new VoiceNoteContent(resource, "audio/mp4", "memo.m4a", 2048L);
        when(voiceNoteService.loadAudio(OWNER_ID, VOICE_NOTE_ID)).thenReturn(audio);

        RuntimeException engineFailure = new RuntimeException("engine unavailable");
        when(transcriptionEngine.transcribe(resource, "audio/mp4")).thenThrow(engineFailure);

        worker.onTranscriptionRequested(new TranscriptionRequestedEvent(TRANSCRIPTION_ID));

        assertThat(row.getStatus()).isEqualTo(TranscriptionStatus.FAILED);
        assertThat(row.getErrorMessage()).isEqualTo("engine unavailable");
        assertThat(row.getTranscript()).isNull();

        verify(eventPublisher).publishEvent(
                new TranscriptionFailedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID, "engine unavailable"));
    }

    @Test
    void onTranscriptionRequested_rowDeletedBeforeMarkFailedCanRun_publishesNoFailedEvent() {
        Transcription row = pendingRow();
        // First read succeeds (process starts); by the time markFailed
        // re-reads, the row is gone — e.g. the voice note (and its
        // transcription) was deleted while the worker was mid-flight.
        when(transcriptionRepository.findById(TRANSCRIPTION_ID))
                .thenReturn(Optional.of(row))
                .thenReturn(Optional.empty());
        when(transcriptionRepository.save(row)).thenReturn(row);
        when(voiceNoteService.loadAudio(OWNER_ID, VOICE_NOTE_ID))
                .thenThrow(new RuntimeException("boom"));

        worker.onTranscriptionRequested(new TranscriptionRequestedEvent(TRANSCRIPTION_ID));

        verifyNoInteractions(eventPublisher);
        // Only the PROCESSING save happened; markFailed found nothing to save.
        verify(transcriptionRepository, times(1)).save(row);
    }

    @Test
    void onTranscriptionRequested_exceptionWithNullMessage_storesNullErrorMessageWithoutThrowing() {
        Transcription row = pendingRow();
        when(transcriptionRepository.findById(TRANSCRIPTION_ID)).thenReturn(Optional.of(row));
        when(transcriptionRepository.save(row)).thenReturn(row);
        // A NullPointerException with no message is a realistic real-world
        // failure mode — getMessage() legitimately returns null.
        when(voiceNoteService.loadAudio(OWNER_ID, VOICE_NOTE_ID)).thenThrow(new NullPointerException());

        worker.onTranscriptionRequested(new TranscriptionRequestedEvent(TRANSCRIPTION_ID));

        assertThat(row.getStatus()).isEqualTo(TranscriptionStatus.FAILED);
        assertThat(row.getErrorMessage()).isNull();
        verify(eventPublisher).publishEvent(
                new TranscriptionFailedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID, null));
    }
}