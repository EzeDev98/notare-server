package com.notare.feature.collaboration.repository;

import com.notare.feature.collaboration.enums.CollaborationStatus;
import com.notare.feature.collaboration.model.Collaboration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollaborationRepository extends JpaRepository<Collaboration, Long> {

    /** Pre-check for a friendly 409 before the unique constraint would fire. */
    boolean existsByOwnerIdAndCollaboratorId(Long ownerId, Long collaboratorId);

    /**
     * "Is this person an ACCEPTED collaborator of mine?" — the gate Step 7 puts
     * in front of every share.
     *
     * <p>Separate from existsByOwnerIdAndCollaboratorId above, which is true for
     * PENDING and REJECTED rows too. Reusing that one here would let an owner
     * share notes with someone who never accepted, or who explicitly declined.
     * The status has to be part of the query, not an afterthought.
     */
    boolean existsByOwnerIdAndCollaboratorIdAndStatus(
            Long ownerId, Long collaboratorId, CollaborationStatus status);

    /**
     * An invitation scoped to its INVITEE — used for accept/reject, so only the
     * person the invitation was sent to can act on it.
     */
    Optional<Collaboration> findByIdAndCollaboratorId(Long id, Long collaboratorId);

    /**
     * An invitation scoped to its OWNER — used for cancel/remove, so only the
     * person who sent it can withdraw it.
     */
    Optional<Collaboration> findByIdAndOwnerId(Long id, Long ownerId);

    /** Invitations I sent (I'm the owner). */
    Page<Collaboration> findByOwnerId(Long ownerId, Pageable pageable);

    /** Invitations I received (I'm the collaborator). */
    Page<Collaboration> findByCollaboratorId(Long collaboratorId, Pageable pageable);

    /**
     * My accepted collaborators, by status. Step 7 uses this (with ACCEPTED) to
     * answer "who am I allowed to share a note with?".
     */
    List<Collaboration> findByOwnerIdAndStatus(Long ownerId, CollaborationStatus status);
}