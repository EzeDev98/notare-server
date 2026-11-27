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

/** Tests for TranscriptionWorker. */
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

        InOrder order = inOrder(transcriptionRepository, transcriptionEngine);
        order.verify(transcriptionRepository).save(row);
        order.verify(transcriptionEngine).transcribe(resource, "audio/mp4");
        order.verify(transcriptionRepository).save(row);
        verify(transcriptionRepository, times(2)).save(row);

        verify(eventPublisher).publishEvent(
                new TranscriptionCompletedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID));
        verify(transcriptionRepository, times(1)).findById(TRANSCRIPTION_ID);
    }

    @Test
    void onTranscriptionRequested_audioLoadFails_marksFailedWithTheExceptionMessageAndPublishesFailedEvent() {
        Transcription row = pendingRow();
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
        when(transcriptionRepository.findById(TRANSCRIPTION_ID))
                .thenReturn(Optional.of(row))
                .thenReturn(Optional.empty());
        when(transcriptionRepository.save(row)).thenReturn(row);
        when(voiceNoteService.loadAudio(OWNER_ID, VOICE_NOTE_ID))
                .thenThrow(new RuntimeException("boom"));

        worker.onTranscriptionRequested(new TranscriptionRequestedEvent(TRANSCRIPTION_ID));

        verifyNoInteractions(eventPublisher);
        verify(transcriptionRepository, times(1)).save(row);
    }

    @Test
    void onTranscriptionRequested_exceptionWithNullMessage_storesNullErrorMessageWithoutThrowing() {
        Transcription row = pendingRow();
        when(transcriptionRepository.findById(TRANSCRIPTION_ID)).thenReturn(Optional.of(row));
        when(transcriptionRepository.save(row)).thenReturn(row);
        when(voiceNoteService.loadAudio(OWNER_ID, VOICE_NOTE_ID)).thenThrow(new NullPointerException());

        worker.onTranscriptionRequested(new TranscriptionRequestedEvent(TRANSCRIPTION_ID));

        assertThat(row.getStatus()).isEqualTo(TranscriptionStatus.FAILED);
        assertThat(row.getErrorMessage()).isNull();
        verify(eventPublisher).publishEvent(
                new TranscriptionFailedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID, null));
    }
}
