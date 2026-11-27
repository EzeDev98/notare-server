package com.notare.feature.transcription.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.TranscriptionNotFoundException;
import com.notare.common.exception.VoiceNoteNotFoundException;
import com.notare.feature.transcription.dto.response.TranscriptionResponse;
import com.notare.feature.transcription.enums.TranscriptionStatus;
import com.notare.feature.transcription.event.TranscriptionRequestedEvent;
import com.notare.feature.transcription.mapper.TranscriptionMapper;
import com.notare.feature.transcription.model.Transcription;
import com.notare.feature.transcription.repository.TranscriptionRepository;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests for TranscriptionServiceImpl. */
@ExtendWith(MockitoExtension.class)
class TranscriptionServiceImplTest {

    private static final Long OWNER_ID = 1L;
    private static final Long VOICE_NOTE_ID = 100L;
    private static final Long TRANSCRIPTION_ID = 500L;

    @Mock
    private TranscriptionRepository transcriptionRepository;

    @Mock
    private TranscriptionMapper transcriptionMapper;

    @Mock
    private VoiceNoteService voiceNoteService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TranscriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TranscriptionServiceImpl(
                transcriptionRepository, transcriptionMapper, voiceNoteService, eventPublisher);
    }

    private VoiceNoteResponse clipOwnedBy(Long ownerId) {
        return new VoiceNoteResponse(VOICE_NOTE_ID, ownerId, null, "clip.m4a", "audio/mp4", 1024L, 30, null, null);
    }

    private VoiceNoteResponse ownClip() {
        return clipOwnedBy(OWNER_ID);
    }

    private Transcription existing(TranscriptionStatus status) {
        Transcription t = new Transcription();
        t.setId(TRANSCRIPTION_ID);
        t.setOwnerId(OWNER_ID);
        t.setVoiceNoteId(VOICE_NOTE_ID);
        t.setStatus(status);
        return t;
    }

    @Test
    void submit_callerDoesNotOwnTheVoiceNote_isRejectedBeforeAnyRepositoryOrEventWork() {
        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID))
                .thenThrow(new VoiceNoteNotFoundException("Voice note not found."));

        assertThatThrownBy(() -> service.submit(OWNER_ID, VOICE_NOTE_ID))
                .isInstanceOf(VoiceNoteNotFoundException.class);

        verify(transcriptionRepository, never()).findByVoiceNoteIdAndOwnerId(any(), any());
        verify(transcriptionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void submit_noExistingJob_createsPendingJobAndPublishesRequestedEvent() {
        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID)).thenReturn(ownClip());
        when(transcriptionRepository.findByVoiceNoteIdAndOwnerId(VOICE_NOTE_ID, OWNER_ID))
                .thenReturn(Optional.empty());

        Transcription saved = existing(TranscriptionStatus.PENDING);
        ArgumentCaptor<Transcription> toSaveCaptor = ArgumentCaptor.forClass(Transcription.class);
        when(transcriptionRepository.save(toSaveCaptor.capture())).thenReturn(saved);

        TranscriptionResponse response = mock(TranscriptionResponse.class);
        when(transcriptionMapper.toResponse(saved)).thenReturn(response);

        TranscriptionResponse result = service.submit(OWNER_ID, VOICE_NOTE_ID);

        assertThat(result).isEqualTo(response);

        Transcription persisted = toSaveCaptor.getValue();
        assertThat(persisted.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(persisted.getVoiceNoteId()).isEqualTo(VOICE_NOTE_ID);
        assertThat(persisted.getStatus()).isEqualTo(TranscriptionStatus.PENDING);

        verify(eventPublisher).publishEvent(new TranscriptionRequestedEvent(TRANSCRIPTION_ID));
    }

    @Test
    void submit_existingPendingJob_isIdempotent_returnsCurrentStateAndPublishesNothing() {
        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID)).thenReturn(ownClip());
        Transcription pending = existing(TranscriptionStatus.PENDING);
        when(transcriptionRepository.findByVoiceNoteIdAndOwnerId(VOICE_NOTE_ID, OWNER_ID))
                .thenReturn(Optional.of(pending));

        TranscriptionResponse response = mock(TranscriptionResponse.class);
        when(transcriptionMapper.toResponse(pending)).thenReturn(response);

        TranscriptionResponse result = service.submit(OWNER_ID, VOICE_NOTE_ID);

        assertThat(result).isEqualTo(response);
        verify(transcriptionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void submit_existingProcessingJob_isIdempotent_doesNotStartASecondRun() {
        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID)).thenReturn(ownClip());
        Transcription processing = existing(TranscriptionStatus.PROCESSING);
        when(transcriptionRepository.findByVoiceNoteIdAndOwnerId(VOICE_NOTE_ID, OWNER_ID))
                .thenReturn(Optional.of(processing));

        TranscriptionResponse response = mock(TranscriptionResponse.class);
        when(transcriptionMapper.toResponse(processing)).thenReturn(response);

        TranscriptionResponse result = service.submit(OWNER_ID, VOICE_NOTE_ID);

        assertThat(result).isEqualTo(response);
        verify(transcriptionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void submit_existingCompletedJob_isIdempotent_returnsTheFinishedResultWithoutRequeuing() {
        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID)).thenReturn(ownClip());
        Transcription completed = existing(TranscriptionStatus.COMPLETED);
        completed.setTranscript("hello world");
        completed.setConfidence(0.9);
        completed.setLanguage("en");
        when(transcriptionRepository.findByVoiceNoteIdAndOwnerId(VOICE_NOTE_ID, OWNER_ID))
                .thenReturn(Optional.of(completed));

        TranscriptionResponse response = mock(TranscriptionResponse.class);
        when(transcriptionMapper.toResponse(completed)).thenReturn(response);

        TranscriptionResponse result = service.submit(OWNER_ID, VOICE_NOTE_ID);

        assertThat(result).isEqualTo(response);
        assertThat(completed.getTranscript()).isEqualTo("hello world");
        verify(transcriptionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void submit_existingFailedJob_resetsEveryResultFieldToPendingAndRepublishesRequested() {
        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID)).thenReturn(ownClip());
        Transcription failed = existing(TranscriptionStatus.FAILED);
        failed.setErrorMessage("engine timed out");
        failed.setTranscript("partial garbage");
        failed.setConfidence(0.1);
        failed.setLanguage("xx");
        when(transcriptionRepository.findByVoiceNoteIdAndOwnerId(VOICE_NOTE_ID, OWNER_ID))
                .thenReturn(Optional.of(failed));
        when(transcriptionRepository.save(failed)).thenReturn(failed);

        TranscriptionResponse response = mock(TranscriptionResponse.class);
        when(transcriptionMapper.toResponse(failed)).thenReturn(response);

        TranscriptionResponse result = service.submit(OWNER_ID, VOICE_NOTE_ID);

        assertThat(result).isEqualTo(response);
        assertThat(failed.getStatus()).isEqualTo(TranscriptionStatus.PENDING);
        assertThat(failed.getErrorMessage()).isNull();
        assertThat(failed.getTranscript()).isNull();
        assertThat(failed.getConfidence()).isNull();
        assertThat(failed.getLanguage()).isNull();

        verify(transcriptionRepository).save(failed);
        verify(eventPublisher).publishEvent(new TranscriptionRequestedEvent(TRANSCRIPTION_ID));
    }

    @Test
    void getByVoiceNote_existingJob_returnsMappedResponse() {
        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID)).thenReturn(ownClip());
        Transcription t = existing(TranscriptionStatus.COMPLETED);
        when(transcriptionRepository.findByVoiceNoteIdAndOwnerId(VOICE_NOTE_ID, OWNER_ID))
                .thenReturn(Optional.of(t));
        TranscriptionResponse response = mock(TranscriptionResponse.class);
        when(transcriptionMapper.toResponse(t)).thenReturn(response);

        TranscriptionResponse result = service.getByVoiceNote(OWNER_ID, VOICE_NOTE_ID);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getByVoiceNote_noJobExists_throwsTranscriptionNotFound() {
        when(voiceNoteService.getVoiceNote(OWNER_ID, VOICE_NOTE_ID)).thenReturn(ownClip());
        when(transcriptionRepository.findByVoiceNoteIdAndOwnerId(VOICE_NOTE_ID, OWNER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByVoiceNote(OWNER_ID, VOICE_NOTE_ID))
                .isInstanceOf(TranscriptionNotFoundException.class);
    }

    @Test
    void searchTranscripts_nullQuery_normalizesToEmptyStringAndScopesToCompleted() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Transcription> page = new PageImpl<>(List.of(), pageable, 0);
        when(transcriptionRepository.searchByOwnerAndStatus(
                eq(OWNER_ID), eq(TranscriptionStatus.COMPLETED), eq(""), eq(pageable)))
                .thenReturn(page);

        PagedResponse<TranscriptionResponse> result = service.searchTranscripts(OWNER_ID, null, pageable);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void searchTranscripts_termIsTrimmedAndLikeEscapedBeforeHittingTheRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        Transcription t = existing(TranscriptionStatus.COMPLETED);
        Page<Transcription> page = new PageImpl<>(List.of(t), pageable, 1);
        when(transcriptionRepository.searchByOwnerAndStatus(
                eq(OWNER_ID), eq(TranscriptionStatus.COMPLETED), eq("50!%!_off"), eq(pageable)))
                .thenReturn(page);

        TranscriptionResponse response = mock(TranscriptionResponse.class);
        when(transcriptionMapper.toResponse(t)).thenReturn(response);

        PagedResponse<TranscriptionResponse> result =
                service.searchTranscripts(OWNER_ID, "  50%_off  ", pageable);

        assertThat(result.content()).containsExactly(response);
    }

    @Test
    void searchTranscripts_mapsEveryPageEntryAndPreservesPagingMetadata() {
        Pageable pageable = PageRequest.of(1, 10);
        Transcription first = existing(TranscriptionStatus.COMPLETED);
        Transcription second = existing(TranscriptionStatus.COMPLETED);
        second.setId(501L);
        Page<Transcription> page = new PageImpl<>(List.of(first, second), pageable, 22);
        when(transcriptionRepository.searchByOwnerAndStatus(anyLong(), eq(TranscriptionStatus.COMPLETED), any(), eq(pageable)))
                .thenReturn(page);

        TranscriptionResponse firstResponse = mock(TranscriptionResponse.class);
        TranscriptionResponse secondResponse = mock(TranscriptionResponse.class);
        when(transcriptionMapper.toResponse(first)).thenReturn(firstResponse);
        when(transcriptionMapper.toResponse(second)).thenReturn(secondResponse);

        PagedResponse<TranscriptionResponse> result = service.searchTranscripts(OWNER_ID, "note", pageable);

        assertThat(result.content()).containsExactly(firstResponse, secondResponse);
        assertThat(result.totalElements()).isEqualTo(22);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isTrue();
    }
}
