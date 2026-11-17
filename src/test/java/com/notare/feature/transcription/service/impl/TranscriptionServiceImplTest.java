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

/**
 * Step 11a of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito, Spring context ({@code ApplicationEventPublisher}),
 * and Spring Data ({@code Page}/{@code Pageable}) are unavailable as
 * compile-time dependencies here, and {@code target/classes} only holds this
 * project's own already-compiled classes, not third-party jars. Every test
 * below is reasoned carefully from {@link TranscriptionServiceImpl}'s source
 * and follows this codebase's existing Mockito conventions exactly, but it has
 * not actually been compiled or run. Please run it via {@code mvn test}
 * before trusting it.
 *
 * <p>Covers {@link TranscriptionServiceImpl#submit}, {@code getByVoiceNote},
 * and {@code searchTranscripts}.
 *
 * <p>The interesting behavior in {@code submit} is entirely in the branching
 * over the four {@link TranscriptionStatus} values for an EXISTING job:
 * PENDING/PROCESSING/COMPLETED must all be idempotent no-ops (return the
 * current row, publish nothing, insert nothing new), while FAILED must be
 * reset back to PENDING and re-queued. That reset also has its own
 * requirement worth locking in: every result field from the failed attempt
 * (transcript, confidence, language, errorMessage) must be cleared, not just
 * the status flipped — otherwise a retried job could briefly show stale
 * COMPLETED-looking data under a PENDING status.
 *
 * <p>Ownership is delegated, not duplicated: {@code submit} calls {@code
 * voiceNoteService.getVoiceNote} purely for its side effect of throwing
 * {@link VoiceNoteNotFoundException} when the caller doesn't own the voice
 * note. Its return value is discarded. Several tests assert this call
 * happens, and that a rejection from it short-circuits everything after —
 * no repository write, no event.
 */
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

    // -------------------------------------------------------------------
    // submit — ownership gate
    // -------------------------------------------------------------------

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

    // -------------------------------------------------------------------
    // submit — no existing job: create PENDING, save, publish
    // -------------------------------------------------------------------

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

    // -------------------------------------------------------------------
    // submit — existing job in a "leave it alone" state
    // -------------------------------------------------------------------

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
        // Nothing about the completed result is touched.
        assertThat(completed.getTranscript()).isEqualTo("hello world");
        verify(transcriptionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // -------------------------------------------------------------------
    // submit — existing FAILED job: reset and re-queue
    // -------------------------------------------------------------------

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
        // Every field a failed attempt could have populated must be cleared,
        // not just the status — a half-reset row would show FAILED-attempt
        // leftovers under a PENDING status.
        assertThat(failed.getStatus()).isEqualTo(TranscriptionStatus.PENDING);
        assertThat(failed.getErrorMessage()).isNull();
        assertThat(failed.getTranscript()).isNull();
        assertThat(failed.getConfidence()).isNull();
        assertThat(failed.getLanguage()).isNull();

        verify(transcriptionRepository).save(failed);
        verify(eventPublisher).publishEvent(new TranscriptionRequestedEvent(TRANSCRIPTION_ID));
    }

    // -------------------------------------------------------------------
    // getByVoiceNote
    // -------------------------------------------------------------------

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

    // -------------------------------------------------------------------
    // searchTranscripts — term normalization, escaping, status scoping
    // -------------------------------------------------------------------

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
        // "50%_off" -> trimmed already, then LikeEscaper turns it into
        // "50!%!_off" so the literal % and _ don't act as wildcards.
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
        // totalElements must exceed 20 (not just 10) so page 1 is a genuine
        // middle page: page 0 covers items 0-9, page 1 covers items 10-19.
        // At total=12, page 1 is the LAST page and hasNext() is correctly
        // false. 22 puts a page 2 after this one, making both hasNext() and
        // hasPrevious() true.
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