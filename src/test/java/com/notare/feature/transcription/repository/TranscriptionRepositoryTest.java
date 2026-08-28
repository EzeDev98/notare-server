package com.notare.feature.transcription.repository;

import com.notare.feature.transcription.enums.TranscriptionStatus;
import com.notare.feature.transcription.model.Transcription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TranscriptionRepositoryTest {

    @Autowired
    private TranscriptionRepository repository;

    private final LocalDateTime base =
            LocalDateTime.of(2026, 1, 1, 12, 0);

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        repository.saveAll(List.of(
                transcription(
                        1L,
                        100L,
                        TranscriptionStatus.COMPLETED,
                        "This is a project planning meeting.",
                        0.95,
                        "en",
                        null
                ),
                transcription(
                        1L,
                        101L,
                        TranscriptionStatus.PENDING,
                        null,
                        null,
                        null,
                        null
                ),
                transcription(
                        1L,
                        102L,
                        TranscriptionStatus.COMPLETED,
                        "Discuss the project deadline and delivery plan.",
                        0.91,
                        "en",
                        null
                ),
                transcription(
                        1L,
                        103L,
                        TranscriptionStatus.FAILED,
                        null,
                        null,
                        null,
                        "Transcription engine failed"
                ),
                transcription(
                        2L,
                        200L,
                        TranscriptionStatus.COMPLETED,
                        "This is another user's project.",
                        0.88,
                        "en",
                        null
                )
        ));

        repository.flush();
    }

    @Test
    void findByIdAndOwnerId_enforces_owner_scope() {
        Transcription own =
                repository.findByVoiceNoteIdAndOwnerId(100L, 1L)
                        .orElseThrow();

        assertThat(
                repository.findByIdAndOwnerId(
                        own.getId(),
                        1L
                )
        ).isPresent();

        assertThat(
                repository.findByIdAndOwnerId(
                        own.getId(),
                        2L
                )
        ).isEmpty();
    }

    @Test
    void findByVoiceNoteIdAndOwnerId_returns_correct_transcription() {
        assertThat(
                repository.findByVoiceNoteIdAndOwnerId(100L, 1L)
        )
                .isPresent()
                .get()
                .extracting(Transcription::getStatus)
                .isEqualTo(TranscriptionStatus.COMPLETED);

        assertThat(
                repository.findByVoiceNoteIdAndOwnerId(100L, 2L)
        ).isEmpty();
    }

    @Test
    void searchByOwnerAndStatus_is_case_insensitive_and_partial() {
        // searchByOwnerAndStatus does a single literal-phrase LIKE match, not a
        // tokenized/AND search - "PROJECT PLAN" only matches a transcript that
        // contains that exact two-word phrase contiguously. Transcript 102
        // ("...delivery plan.") has "project" and "plan" nowhere near each
        // other, so it was never a match under that semantic; asserting it was
        // is what made this test flaky against the real query. "ROJEC" (mixed
        // case, and not a whole word) still exercises both properties this
        // test is named for - case-insensitivity and partial/substring
        // matching - against the "project" substring both transcripts share.
        assertThat(
                repository.searchByOwnerAndStatus(
                        1L,
                        TranscriptionStatus.COMPLETED,
                        "ROJEC",
                        PageRequest.of(0, 20, Sort.by("id"))
                ).getContent()
        )
                .extracting(Transcription::getVoiceNoteId)
                .containsExactly(100L, 102L);
    }

    @Test
    void searchByOwnerAndStatus_excludes_non_matching_statuses() {
        assertThat(
                repository.searchByOwnerAndStatus(
                        1L,
                        TranscriptionStatus.COMPLETED,
                        "project",
                        PageRequest.of(0, 20)
                ).getContent()
        )
                .allMatch(t ->
                        t.getStatus() == TranscriptionStatus.COMPLETED);

        assertThat(
                repository.searchByOwnerAndStatus(
                        1L,
                        TranscriptionStatus.PENDING,
                        "project",
                        PageRequest.of(0, 20)
                ).getContent()
        ).isEmpty();
    }

    @Test
    void searchByOwnerAndStatus_does_not_leak_other_users_transcripts() {
        assertThat(
                repository.searchByOwnerAndStatus(
                        1L,
                        TranscriptionStatus.COMPLETED,
                        "another user's project",
                        PageRequest.of(0, 20)
                ).getContent()
        ).isEmpty();
    }

    @Test
    void search_handles_empty_result() {
        assertThat(
                repository.searchByOwnerAndStatus(
                        1L,
                        TranscriptionStatus.COMPLETED,
                        "does-not-exist",
                        PageRequest.of(0, 20)
                ).getContent()
        ).isEmpty();
    }

    private Transcription transcription(
            Long ownerId,
            Long voiceNoteId,
            TranscriptionStatus status,
            String transcript,
            Double confidence,
            String language,
            String errorMessage
    ) {
        return new Transcription(
                null,
                ownerId,
                voiceNoteId,
                status,
                transcript,
                confidence,
                language,
                errorMessage,
                base,
                base.plusMinutes(1)
        );
    }
}
