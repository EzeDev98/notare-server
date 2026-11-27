package com.notare.feature.transcription.mapper;

import com.notare.feature.transcription.dto.response.TranscriptionResponse;
import com.notare.feature.transcription.enums.TranscriptionStatus;
import com.notare.feature.transcription.model.Transcription;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for TranscriptionMapper. */
class TranscriptionMapperTest {

    private final TranscriptionMapper mapper = new TranscriptionMapper();

    @Test
    void toResponse_pendingHasNoTranscriptConfidenceOrError() {
        Transcription transcription = new Transcription();
        transcription.setId(1L);
        transcription.setVoiceNoteId(2L);
        transcription.setStatus(TranscriptionStatus.PENDING);

        TranscriptionResponse response = mapper.toResponse(transcription);

        assertThat(response.status()).isEqualTo(TranscriptionStatus.PENDING);
        assertThat(response.transcript()).isNull();
        assertThat(response.confidence()).isNull();
        assertThat(response.language()).isNull();
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void toResponse_completedCarriesTranscriptConfidenceAndLanguage() {
        Transcription transcription = new Transcription();
        transcription.setVoiceNoteId(2L);
        transcription.setStatus(TranscriptionStatus.COMPLETED);
        transcription.setTranscript("hello world");
        transcription.setConfidence(0.95);
        transcription.setLanguage("en");

        TranscriptionResponse response = mapper.toResponse(transcription);

        assertThat(response.status()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(response.transcript()).isEqualTo("hello world");
        assertThat(response.confidence()).isEqualTo(0.95);
        assertThat(response.language()).isEqualTo("en");
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void toResponse_failedCarriesTheErrorMessageInsteadOfATranscript() {
        Transcription transcription = new Transcription();
        transcription.setVoiceNoteId(2L);
        transcription.setStatus(TranscriptionStatus.FAILED);
        transcription.setErrorMessage("unsupported codec");

        TranscriptionResponse response = mapper.toResponse(transcription);

        assertThat(response.status()).isEqualTo(TranscriptionStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("unsupported codec");
        assertThat(response.transcript()).isNull();
    }

    @Test
    void toResponse_mapsIdAndVoiceNoteIdWithoutTransposingThem() {
        Transcription transcription = new Transcription();
        transcription.setId(1L);
        transcription.setVoiceNoteId(2L);
        transcription.setStatus(TranscriptionStatus.PROCESSING);

        TranscriptionResponse response = mapper.toResponse(transcription);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.voiceNoteId()).isEqualTo(2L);
    }
}
