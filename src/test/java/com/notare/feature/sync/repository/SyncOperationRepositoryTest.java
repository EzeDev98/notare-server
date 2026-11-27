package com.notare.feature.sync.repository;

import com.notare.feature.sync.model.SyncOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for SyncOperationRepository. */
@DataJpaTest
@ActiveProfiles("test")
class SyncOperationRepositoryTest {

    @Autowired
    private SyncOperationRepository repository;

    @Autowired
    private TestEntityManager testEntityManager;

    private final LocalDateTime base =
            LocalDateTime.of(2026, 1, 1, 12, 0);

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        Map<String, LocalDateTime> intendedCreatedAt = Map.of(
                "1:op-001", base.plusHours(1),
                "1:op-002", base.plusHours(2),
                "2:op-001", base.plusHours(3)
        );

        List<SyncOperation> saved = repository.saveAll(List.of(
                operation(
                        1L,
                        "op-001",
                        100L,
                        4L,
                        base.plusHours(1)
                ),
                operation(
                        1L,
                        "op-002",
                        101L,
                        7L,
                        base.plusHours(2)
                ),
                operation(
                        2L,
                        "op-001",
                        200L,
                        3L,
                        base.plusHours(3)
                )
        ));

        repository.flush();

        for (SyncOperation op : saved) {
            testEntityManager.getEntityManager()
                    .createQuery("update SyncOperation s set s.createdAt = :createdAt where s.id = :id")
                    .setParameter("createdAt", intendedCreatedAt.get(op.getOwnerId() + ":" + op.getOperationId()))
                    .setParameter("id", op.getId())
                    .executeUpdate();
        }
        testEntityManager.getEntityManager().clear();
    }

    @Test
    void findByOwnerIdAndOperationId_returns_existing_operation() {
        assertThat(
                repository.findByOwnerIdAndOperationId(
                        1L,
                        "op-001"
                )
        )
                .isPresent()
                .get()
                .satisfies(operation -> {
                    assertThat(operation.getNoteId()).isEqualTo(100L);
                    assertThat(operation.getResultVersion()).isEqualTo(4L);
                });
    }

    @Test
    void operation_id_is_scoped_by_owner() {
        assertThat(
                repository.findByOwnerIdAndOperationId(
                        1L,
                        "op-001"
                )
        ).isPresent();

        assertThat(
                repository.findByOwnerIdAndOperationId(
                        2L,
                        "op-001"
                )
        ).isPresent();

        assertThat(
                repository.findByOwnerIdAndOperationId(
                        3L,
                        "op-001"
                )
        ).isEmpty();
    }

    @Test
    void unknown_operation_returns_empty() {
        assertThat(
                repository.findByOwnerIdAndOperationId(
                        1L,
                        "missing-operation"
                )
        ).isEmpty();
    }

    @Test
    void deleteOlderThan_removes_only_old_ledger_entries() {
        int deleted =
                repository.deleteOlderThan(base.plusHours(3));

        repository.flush();

        assertThat(deleted).isEqualTo(2);

        assertThat(repository.findAll())
                .extracting(SyncOperation::getOperationId)
                .containsExactly("op-001");
    }

    @Test
    void deleteOlderThan_does_not_delete_entry_at_cutoff() {
        int deleted =
                repository.deleteOlderThan(base.plusHours(1));

        repository.flush();

        assertThat(deleted).isZero();

        assertThat(repository.findAll())
                .extracting(SyncOperation::getOperationId)
                .containsExactlyInAnyOrder(
                        "op-001",
                        "op-002",
                        "op-001"
                );
    }

    @Test
    void unique_constraint_prevents_duplicate_owner_operation_pair() {
        SyncOperation duplicate =
                operation(
                        1L,
                        "op-001",
                        999L,
                        99L,
                        base.plusHours(10)
                );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            repository.saveAndFlush(duplicate);
        }).isNotNull();
    }

    private static SyncOperation operation(
            Long ownerId,
            String operationId,
            Long noteId,
            Long resultVersion,
            LocalDateTime createdAt
    ) {
        return new SyncOperation(
                null,
                ownerId,
                operationId,
                noteId,
                resultVersion,
                createdAt
        );
    }
}
