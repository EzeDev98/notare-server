package com.notare.feature.transcription.mapper;

import com.notare.feature.transcription.dto.response.TranscriptionResponse;
import com.notare.feature.transcription.enums.TranscriptionStatus;
import com.notare.feature.transcription.model.Transcription;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3e of the coverage roadmap.
 *
 * <p>Covers {@link TranscriptionMapper#toResponse} across each lifecycle
 * stage {@link TranscriptionStatus} describes: PENDING/PROCESSING (transcript
 * and friends still null), COMPLETED (transcript, confidence, and language
 * populated), and FAILED (errorMessage populated instead). The mapper itself
 * has no branching on status - it's a straight field copy - but the response
 * DTO's own doc comment says the client's contract depends on which fields
 * are populated for which status, so that contract is what's worth testing,
 * not just "does the copy work."
 */
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