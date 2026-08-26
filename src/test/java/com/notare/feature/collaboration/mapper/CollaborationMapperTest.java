package com.notare.feature.collaboration.mapper;

import com.notare.feature.collaboration.dto.response.CollaborationResponse;
import com.notare.feature.collaboration.enums.CollaborationStatus;
import com.notare.feature.collaboration.model.Collaboration;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3c of the coverage roadmap.
 *
 * <p>Covers {@link CollaborationMapper#toResponse}. Small and simple, but
 * {@code ownerId} and {@code collaboratorId} are both plain {@code Long}
 * fields sitting next to each other in both the entity and the constructor
 * call - exactly the shape a copy-paste transposition slips into unnoticed,
 * so each is asserted against a distinct value rather than glossed over with
 * a single reflection-based equality check. One test per {@link
 * CollaborationStatus} constant, since the mapper does nothing status-
 * specific but a future change easily could.
 */
class CollaborationMapperTest {

    private final CollaborationMapper mapper = new CollaborationMapper();

    private Collaboration collaboration(CollaborationStatus status) {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 1, 2, 11, 0);

        Collaboration c = new Collaboration();
        c.setId(10L);
        c.setOwnerId(20L);
        c.setCollaboratorId(30L);
        c.setStatus(status);
        c.setCreatedAt(created);
        c.setUpdatedAt(updated);
        return c;
    }

    @Test
    void toResponse_mapsIdOwnerAndCollaboratorWithoutTransposingThem() {
        CollaborationResponse response = mapper.toResponse(collaboration(CollaborationStatus.PENDING));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.ownerId()).isEqualTo(20L);
        assertThat(response.collaboratorId()).isEqualTo(30L);
    }

    @Test
    void toResponse_mapsTimestamps() {
        CollaborationResponse response = mapper.toResponse(collaboration(CollaborationStatus.PENDING));

        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 11, 0));
    }

    @Test
    void toResponse_preservesPendingStatus() {
        assertThat(mapper.toResponse(collaboration(CollaborationStatus.PENDING)).status())
                .isEqualTo(CollaborationStatus.PENDING);
    }

    @Test
    void toResponse_preservesAcceptedStatus() {
        assertThat(mapper.toResponse(collaboration(CollaborationStatus.ACCEPTED)).status())
                .isEqualTo(CollaborationStatus.ACCEPTED);
    }

    @Test
    void toResponse_preservesRejectedStatus() {
        assertThat(mapper.toResponse(collaboration(CollaborationStatus.REJECTED)).status())
                .isEqualTo(CollaborationStatus.REJECTED);
    }
}