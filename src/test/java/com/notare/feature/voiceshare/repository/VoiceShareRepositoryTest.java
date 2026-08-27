package com.notare.feature.voiceshare.repository;

import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.voiceshare.model.VoiceShare;
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
class VoiceShareRepositoryTest {

    @Autowired
    private VoiceShareRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        repository.saveAll(List.of(
                share(100L, 1L, 2L, SharePermission.READ),
                share(101L, 1L, 3L, SharePermission.WRITE),
                share(102L, 1L, 4L, SharePermission.READ),
                share(103L, 2L, 1L, SharePermission.WRITE)
        ));

        repository.flush();
    }

    @Test
    void findByVoiceNoteIdAndSharedWithUserId_returns_matching_grant() {
        assertThat(
                repository.findByVoiceNoteIdAndSharedWithUserId(100L, 2L)
        )
                .isPresent()
                .get()
                .extracting(VoiceShare::getPermission)
                .isEqualTo(SharePermission.READ);
    }

    @Test
    void recipient_lookup_does_not_return_grant_for_wrong_user() {
        assertThat(
                repository.findByVoiceNoteIdAndSharedWithUserId(100L, 999L)
        ).isEmpty();
    }

    @Test
    void owner_side_lookup_requires_all_three_scope_values() {
        assertThat(
                repository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                        101L,
                        1L,
                        3L
                )
        ).isPresent();

        assertThat(
                repository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                        101L,
                        2L,
                        3L
                )
        ).isEmpty();

        assertThat(
                repository.findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
                        101L,
                        1L,
                        999L
                )
        ).isEmpty();
    }

    @Test
    void existsByVoiceNoteIdAndSharedWithUserId_detects_existing_grant() {
        assertThat(
                repository.existsByVoiceNoteIdAndSharedWithUserId(100L, 2L)
        ).isTrue();

        assertThat(
                repository.existsByVoiceNoteIdAndSharedWithUserId(100L, 999L)
        ).isFalse();
    }

    @Test
    void findByVoiceNoteIdAndOwnerId_returns_only_owner_grants() {
        assertThat(
                repository.findByVoiceNoteIdAndOwnerId(
                        100L,
                        1L,
                        PageRequest.of(0, 20, Sort.by("id"))
                ).getContent()
        )
                .extracting(VoiceShare::getSharedWithUserId)
                .containsExactly(2L);

        assertThat(
                repository.findByVoiceNoteIdAndOwnerId(
                        100L,
                        2L,
                        PageRequest.of(0, 20)
                ).getContent()
        ).isEmpty();
    }

    @Test
    void findBySharedWithUserId_returns_recipient_collection() {
        assertThat(
                repository.findBySharedWithUserId(
                        1L,
                        PageRequest.of(0, 20, Sort.by("id"))
                ).getContent()
        )
                .extracting(VoiceShare::getVoiceNoteId)
                .containsExactly(103L);
    }

    @Test
    void deleteByOwnerIdAndSharedWithUserId_removes_only_matching_grants() {
        repository.deleteByOwnerIdAndSharedWithUserId(1L, 3L);
        repository.flush();

        assertThat(
                repository.findByVoiceNoteIdAndSharedWithUserId(101L, 3L)
        ).isEmpty();

        assertThat(
                repository.findByVoiceNoteIdAndSharedWithUserId(100L, 2L)
        ).isPresent();

        assertThat(
                repository.findByVoiceNoteIdAndSharedWithUserId(103L, 1L)
        ).isPresent();
    }

    @Test
    void deleteByVoiceNoteId_removes_all_grants_for_voice_note() {
        repository.deleteByVoiceNoteId(100L);
        repository.flush();

        assertThat(
                repository.findByVoiceNoteIdAndSharedWithUserId(100L, 2L)
        ).isEmpty();

        assertThat(
                repository.findByVoiceNoteIdAndSharedWithUserId(101L, 3L)
        ).isPresent();
    }

    private static VoiceShare share(
            Long voiceNoteId,
            Long ownerId,
            Long sharedWithUserId,
            SharePermission permission
    ) {
        return new VoiceShare(
                null,
                voiceNoteId,
                ownerId,
                sharedWithUserId,
                permission,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
