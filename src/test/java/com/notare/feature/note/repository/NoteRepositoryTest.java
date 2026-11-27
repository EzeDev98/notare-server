package com.notare.feature.note.repository;

import com.notare.feature.note.model.Note;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for NoteRepository. */
@DataJpaTest
@ActiveProfiles("test")
class NoteRepositoryTest {

    @Autowired
    private NoteRepository repository;

    @Autowired
    private TestEntityManager testEntityManager;

    private final LocalDateTime base =
            LocalDateTime.of(2026, 1, 1, 12, 0);

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        Map<String, LocalDateTime> intendedUpdatedAt = Map.of(
                "a1", base.plusHours(1),
                "a2", base.plusHours(2),
                "a3", base.plusHours(3),
                "a4", base.plusHours(4),
                "b1", base.plusHours(5)
        );

        List<Note> saved = repository.saveAll(List.of(
                note(
                        1L,
                        "a1",
                        "Alpha",
                        false,
                        false,
                        base.plusHours(1)
                ),
                note(
                        1L,
                        "a2",
                        "Beta",
                        false,
                        false,
                        base.plusHours(2)
                ),
                note(
                        1L,
                        "a3",
                        "Deleted Alpha",
                        false,
                        true,
                        base.plusHours(3)
                ),
                note(
                        1L,
                        "a4",
                        "Deleted Archive",
                        false,
                        true,
                        base.plusHours(4)
                ),
                note(
                        2L,
                        "b1",
                        "Other owner",
                        false,
                        false,
                        base.plusHours(5)
                )
        ));

        for (Note n : saved) {
            testEntityManager.getEntityManager()
                    .createQuery("update Note n set n.updatedAt = :updatedAt where n.id = :id")
                    .setParameter("updatedAt", intendedUpdatedAt.get(n.getClientId()))
                    .setParameter("id", n.getId())
                    .executeUpdate();
        }
        testEntityManager.getEntityManager().clear();
    }

    @Test
    void unscoped_active_finders_exclude_deleted_rows() {
        Note active = repository.findByOwnerIdAndClientId(1L, "a1")
                .orElseThrow();

        Note deleted = repository.findByOwnerIdAndClientId(1L, "a3")
                .orElseThrow();

        assertThat(
                repository.findByIdAndDeletedFalse(active.getId())
        ).isPresent();

        assertThat(
                repository.findByIdAndDeletedFalse(deleted.getId())
        ).isEmpty();

        assertThat(
                repository.findByIdInAndDeletedFalse(
                        List.of(active.getId(), deleted.getId())
                )
        )
                .extracting(Note::getId)
                .containsExactly(active.getId());
    }

    @Test
    void delta_query_includes_deleted_notes_and_respects_owner_and_cursor() {
        Page<Note> result =
                repository.findByOwnerIdAndUpdatedAtGreaterThanEqual(
                        1L,
                        base.plusHours(3),
                        PageRequest.of(
                                0,
                                20,
                                Sort.by("updatedAt").and(Sort.by("id"))
                        )
                );

        assertThat(result.getContent())
                .extracting(Note::getClientId)
                .containsExactly("a3", "a4");
    }

    @Test
    void searchByOwner_is_case_insensitive_partial_and_active_only() {
        Page<Note> result =
                repository.searchByOwner(
                        1L,
                        "ALPHA",
                        PageRequest.of(0, 20, Sort.by("id"))
                );

        assertThat(result.getContent())
                .extracting(Note::getClientId)
                .containsExactly("a1");
    }

    @Test
    void client_id_is_scoped_to_owner() {
        assertThat(
                repository.findByOwnerIdAndClientId(1L, "a1")
        ).isPresent();

        assertThat(
                repository.findByOwnerIdAndClientId(2L, "a1")
        ).isEmpty();
    }

    private static Note note(
            Long ownerId,
            String clientId,
            String content,
            boolean pinned,
            boolean deleted,
            LocalDateTime updatedAt
    ) {
        Note note = new Note();

        note.setOwnerId(ownerId);
        note.setClientId(clientId);
        note.setContent(content);
        note.setPinned(pinned);
        note.setDeleted(deleted);
        note.setDeletedAt(deleted ? updatedAt : null);
        note.setCreatedAt(updatedAt.minusHours(1));
        note.setUpdatedAt(updatedAt);

        return note;
    }
}
