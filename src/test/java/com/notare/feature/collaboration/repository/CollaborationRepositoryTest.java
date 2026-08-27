package com.notare.feature.collaboration.repository;

import com.notare.feature.collaboration.enums.CollaborationStatus;
import com.notare.feature.collaboration.model.Collaboration;
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
class CollaborationRepositoryTest {

    @Autowired
    private CollaborationRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        repository.saveAll(List.of(
                collaboration(
                        1L,
                        2L,
                        CollaborationStatus.PENDING
                ),
                collaboration(
                        1L,
                        3L,
                        CollaborationStatus.ACCEPTED
                ),
                collaboration(
                        2L,
                        1L,
                        CollaborationStatus.ACCEPTED
                ),
                collaboration(
                        3L,
                        1L,
                        CollaborationStatus.REJECTED
                )
        ));
    }

    @Test
    void findByOwnerAndCollaborator_finds_relationship_regardless_of_status() {
        assertThat(
                repository.findByOwnerIdAndCollaboratorId(1L, 3L)
        )
                .isPresent()
                .get()
                .extracting(Collaboration::getStatus)
                .isEqualTo(CollaborationStatus.ACCEPTED);

        assertThat(
                repository.findByOwnerIdAndCollaboratorId(99L, 99L)
        ).isEmpty();
    }

    @Test
    void accepted_status_exists_check_is_status_specific() {
        assertThat(
                repository.existsByOwnerIdAndCollaboratorIdAndStatus(
                        1L,
                        3L,
                        CollaborationStatus.ACCEPTED
                )
        ).isTrue();

        assertThat(
                repository.existsByOwnerIdAndCollaboratorIdAndStatus(
                        1L,
                        2L,
                        CollaborationStatus.ACCEPTED
                )
        ).isFalse();
    }

    @Test
    void invitation_lookup_is_scoped_to_the_correct_party() {
        Long id =
                repository.findByOwnerIdAndCollaboratorId(1L, 2L)
                        .orElseThrow()
                        .getId();

        assertThat(
                repository.findByIdAndCollaboratorId(id, 2L)
        ).isPresent();

        assertThat(
                repository.findByIdAndCollaboratorId(id, 1L)
        ).isEmpty();

        assertThat(
                repository.findByIdAndOwnerId(id, 1L)
        ).isPresent();

        assertThat(
                repository.findByIdAndOwnerId(id, 2L)
        ).isEmpty();
    }

    @Test
    void owner_and_recipient_pages_are_independently_scoped() {
        PageRequest page =
                PageRequest.of(0, 20, Sort.by("id"));

        assertThat(
                repository.findByOwnerId(1L, page).getContent()
        )
                .extracting(Collaboration::getCollaboratorId)
                .containsExactly(2L, 3L);

        assertThat(
                repository.findByCollaboratorId(1L, page).getContent()
        )
                .extracting(Collaboration::getOwnerId)
                .containsExactly(2L, 3L);
    }

    @Test
    void findByOwnerAndStatus_returns_only_requested_status() {
        assertThat(
                repository.findByOwnerIdAndStatus(
                        1L,
                        CollaborationStatus.ACCEPTED
                )
        )
                .extracting(Collaboration::getCollaboratorId)
                .containsExactly(3L);

        assertThat(
                repository.findByOwnerIdAndStatus(
                        1L,
                        CollaborationStatus.PENDING
                )
        )
                .extracting(Collaboration::getCollaboratorId)
                .containsExactly(2L);
    }

    private static Collaboration collaboration(
            Long ownerId,
            Long collaboratorId,
            CollaborationStatus status
    ) {
        return new Collaboration(
                null,
                ownerId,
                collaboratorId,
                status,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null
        );
    }
}
