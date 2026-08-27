package com.notare.feature.share.repository;

import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.model.NoteShare;
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

@DataJpaTest
@ActiveProfiles("test")
class NoteShareRepositoryTest {

    @Autowired
    private NoteShareRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        repository.saveAll(List.of(
                share(
                        10L,
                        1L,
                        2L,
                        SharePermission.READ
                ),
                share(
                        11L,
                        1L,
                        3L,
                        SharePermission.WRITE
                ),
                share(
                        12L,
                        2L,
                        1L,
                        SharePermission.READ
                )
        ));
    }

    @Test
    void recipient_lookup_returns_grant_for_note_and_user() {
        assertThat(
                repository.findByNoteIdAndSharedWithUserId(10L, 2L)
        )
                .isPresent()
                .get()
                .extracting(NoteShare::getPermission)
                .isEqualTo(SharePermission.READ);

        assertThat(
                repository.findByNoteIdAndSharedWithUserId(10L, 3L)
        ).isEmpty();
    }

    @Test
    void owner_side_lookup_requires_note_owner() {
        assertThat(
                repository.findByNoteIdAndOwnerIdAndSharedWithUserId(
                        10L,
                        1L,
                        2L
                )
        ).isPresent();

        assertThat(
                repository.findByNoteIdAndOwnerIdAndSharedWithUserId(
                        10L,
                        2L,
                        2L
                )
        ).isEmpty();
    }

    @Test
    void exists_check_detects_duplicate_grants() {
        assertThat(
                repository.existsByNoteIdAndSharedWithUserId(
                        10L,
                        2L
                )
        ).isTrue();

        assertThat(
                repository.existsByNoteIdAndSharedWithUserId(
                        10L,
                        99L
                )
        ).isFalse();
    }

    @Test
    void owner_and_recipient_pages_are_scoped_correctly() {
        PageRequest page =
                PageRequest.of(0, 20, Sort.by("id"));

        assertThat(
                repository.findByNoteIdAndOwnerId(
                        10L,
                        1L,
                        page
                ).getContent()
        )
                .extracting(NoteShare::getSharedWithUserId)
                .containsExactly(2L);

        assertThat(
                repository.findByNoteIdAndOwnerId(
                        10L,
                        2L,
                        page
                ).getContent()
        ).isEmpty();

        assertThat(
                repository.findBySharedWithUserId(
                        1L,
                        page
                ).getContent()
        )
                .extracting(NoteShare::getNoteId)
                .containsExactly(12L);
    }

    @Test
    void delete_by_owner_and_recipient_removes_only_matching_grants() {
        repository.deleteByOwnerIdAndSharedWithUserId(1L, 2L);
        repository.flush();

        assertThat(
                repository.findByNoteIdAndSharedWithUserId(
                        10L,
                        2L
                )
        ).isEmpty();

        assertThat(
                repository.findByNoteIdAndSharedWithUserId(
                        11L,
                        3L
                )
        ).isPresent();

        assertThat(
                repository.findByNoteIdAndSharedWithUserId(
                        12L,
                        1L
                )
        ).isPresent();
    }

    @Test
    void delete_by_note_removes_all_grants_for_that_note() {
        repository.deleteByNoteId(10L);
        repository.flush();

        assertThat(
                repository.findByNoteIdAndSharedWithUserId(
                        10L,
                        2L
                )
        ).isEmpty();

        assertThat(
                repository.findByNoteIdAndSharedWithUserId(
                        11L,
                        3L
                )
        ).isPresent();
    }

    private static NoteShare share(
            Long noteId,
            Long ownerId,
            Long sharedWithUserId,
            SharePermission permission
    ) {
        return new NoteShare(
                null,
                noteId,
                ownerId,
                sharedWithUserId,
                permission,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
