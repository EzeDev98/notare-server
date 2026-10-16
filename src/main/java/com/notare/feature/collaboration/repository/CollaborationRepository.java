package com.notare.feature.collaboration.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.notare.feature.collaboration.enums.CollaborationStatus;
import com.notare.feature.collaboration.model.Collaboration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollaborationRepository extends JpaRepository<Collaboration, Long> {

    /** Every connection this user is on either side of (step 17g). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from collaborations where owner_id = :userId or collaborator_id = :userId", nativeQuery = true)
    int deleteAllInvolving(@Param("userId") Long userId);


    /**
     * Finds the existing relationship between an owner and collaborator,
     * regardless of its current status.
     *
     * This is intentionally different from an exists check because a
     * REJECTED relationship can be reused for a new invitation.
     */
    Optional<Collaboration> findByOwnerIdAndCollaboratorId(
            Long ownerId,
            Long collaboratorId
    );

    /**
     * "Is this person an ACCEPTED collaborator of mine?"
     */
    boolean existsByOwnerIdAndCollaboratorIdAndStatus(
            Long ownerId,
            Long collaboratorId,
            CollaborationStatus status
    );

    /**
     * An invitation scoped to its INVITEE.
     */
    Optional<Collaboration> findByIdAndCollaboratorId(
            Long id,
            Long collaboratorId
    );

    /**
     * An invitation scoped to its OWNER.
     */
    Optional<Collaboration> findByIdAndOwnerId(
            Long id,
            Long ownerId
    );

    /** Invitations I sent. */
    Page<Collaboration> findByOwnerId(Long ownerId, Pageable pageable);

    /** Invitations I received. */
    Page<Collaboration> findByCollaboratorId(
            Long collaboratorId,
            Pageable pageable
    );

    /** My accepted collaborators. */
    List<Collaboration> findByOwnerIdAndStatus(
            Long ownerId,
            CollaborationStatus status
    );
}
