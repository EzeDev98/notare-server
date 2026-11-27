package com.notare.feature.voicenote.repository;

import com.notare.feature.voicenote.model.VoiceNote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for VoiceNoteRepository. */
@DataJpaTest
@ActiveProfiles("test")
class VoiceNoteRepositoryTest {

    @Autowired
    private VoiceNoteRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        repository.saveAll(List.of(
                voice(1L, 100L, "one.mp3"),
                voice(1L, 100L, "two.mp3"),
                voice(1L, 101L, "three.mp3"),
                voice(2L, 100L, "other.mp3")
        ));
    }

    @Test
    void findByIdAndOwnerId_prevents_cross_owner_lookup() {
        VoiceNote own =
                repository.findByOwnerIdAndNoteId(1L, 100L)
                        .get(0);

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
    void owner_page_contains_only_that_owners_voice_notes() {
        assertThat(
                repository.findByOwnerId(
                        1L,
                        PageRequest.of(
                                0,
                                20,
                                Sort.by("id")
                        )
                ).getContent()
        )
                .hasSize(3)
                .allMatch(
                        voiceNote ->
                                voiceNote.getOwnerId().equals(1L)
                );
    }

    @Test
    void owner_and_note_lookup_is_scoped_to_both_values() {
        assertThat(
                repository.findByOwnerIdAndNoteId(
                        1L,
                        100L
                )
        )
                .extracting(VoiceNote::getOriginalFilename)
                .containsExactly(
                        "one.mp3",
                        "two.mp3"
                );

        assertThat(
                repository.findByOwnerIdAndNoteId(
                        2L,
                        100L
                )
        )
                .extracting(VoiceNote::getOriginalFilename)
                .containsExactly("other.mp3");
    }

    @Test
    void batch_lookup_returns_requested_ids_without_ownership_filter() {
        List<VoiceNote> all =
                repository.findAll(Sort.by("id"));

        Long first = all.get(0).getId();
        Long fourth = all.get(3).getId();

        assertThat(
                repository.findByIdIn(
                        List.of(first, fourth)
                )
        )
                .extracting(VoiceNote::getId)
                .containsExactlyInAnyOrder(
                        first,
                        fourth
                );
    }

    @Test
    void findByNoteId_returns_contributions_from_multiple_owners() {
        assertThat(repository.findByNoteId(100L))
                .extracting(VoiceNote::getOwnerId)
                .containsExactlyInAnyOrder(
                        1L,
                        1L,
                        2L
                );
    }

    private static VoiceNote voice(
            Long ownerId,
            Long noteId,
            String filename
    ) {
        return new VoiceNote(
                null,
                ownerId,
                noteId,
                filename,
                "audio/mpeg",
                "voice/" + filename,
                1024L,
                30,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                false,
                null,
                false
        );
    }
}
