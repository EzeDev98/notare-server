package com.notare.feature.sync.repository;

import com.notare.feature.sync.model.NoteTombstone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for NoteTombstoneRepository. */
@DataJpaTest
@ActiveProfiles("test")
class NoteTombstoneRepositoryTest {

    @Autowired
    private NoteTombstoneRepository repository;

    private final LocalDateTime base =
            LocalDateTime.of(2026, 1, 1, 12, 0);

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        repository.saveAll(List.of(
                tombstone(100L, 1L, base.plusHours(1)),
                tombstone(101L, 1L, base.plusHours(2)),
                tombstone(102L, 2L, base.plusHours(3)),
                tombstone(103L, 1L, base.plusHours(4))
        ));

        repository.flush();
    }

    @Test
    void findByOwnerIdAndDeletedAtGreaterThanEqual_filters_by_owner_and_cursor() {
        List<NoteTombstone> result =
                repository.findByOwnerIdAndDeletedAtGreaterThanEqual(
                        1L,
                        base.plusHours(2)
                );

        assertThat(result)
                .extracting(NoteTombstone::getNoteId)
                .containsExactlyInAnyOrder(101L, 103L);
    }

    @Test
    void tombstone_query_includes_exact_cursor_timestamp() {
        List<NoteTombstone> result =
                repository.findByOwnerIdAndDeletedAtGreaterThanEqual(
                        1L,
                        base.plusHours(2)
                );

        assertThat(result)
                .extracting(NoteTombstone::getNoteId)
                .contains(101L);
    }

    @Test
    void tombstone_query_does_not_return_other_users_rows() {
        List<NoteTombstone> result =
                repository.findByOwnerIdAndDeletedAtGreaterThanEqual(
                        1L,
                        base
                );

        assertThat(result)
                .extracting(NoteTombstone::getOwnerId)
                .containsOnly(1L);
    }

    @Test
    void deleteOlderThan_removes_only_rows_before_cutoff() {
        int deleted =
                repository.deleteOlderThan(base.plusHours(3));

        repository.flush();

        assertThat(deleted).isEqualTo(2);

        assertThat(repository.findAll())
                .extracting(NoteTombstone::getNoteId)
                .containsExactlyInAnyOrder(102L, 103L);
    }

    @Test
    void deleteOlderThan_does_not_delete_row_at_cutoff() {
        int deleted =
                repository.deleteOlderThan(base.plusHours(1));

        repository.flush();

        assertThat(deleted).isZero();

        assertThat(repository.findAll())
                .extracting(NoteTombstone::getNoteId)
                .contains(100L);
    }

    private static NoteTombstone tombstone(
            Long noteId,
            Long ownerId,
            LocalDateTime deletedAt
    ) {
        return new NoteTombstone(
                null,
                noteId,
                ownerId,
                deletedAt
        );
    }
}
