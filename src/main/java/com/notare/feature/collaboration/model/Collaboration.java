package com.notare.feature.collaboration.model;

import com.notare.feature.collaboration.enums.CollaborationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * A collaboration relationship / invitation between two users. Both parties are
 * stored as plain Long ids (decoupled, like everywhere else). The unique
 * constraint on (owner_id, collaborator_id) is what enforces "you can't invite
 * the same person twice" at the database level.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "collaborations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_collaborations_owner_collaborator",
                columnNames = {"owner_id", "collaborator_id"}
        ),
        indexes = @Index(name = "idx_collaborations_collaborator_id", columnList = "collaborator_id")
)
public class Collaboration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who sent the invitation (the inviter). */
    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    /** The invited user. */
    @Column(name = "collaborator_id", nullable = false, updatable = false)
    private Long collaboratorId;

    /** STRING, not ORDINAL — same reasoning as every other entity enum. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CollaborationStatus status = CollaborationStatus.PENDING;

    /** When the invitation was sent. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Last status change (accept/reject time). */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Who last changed the status.
     *
     * <p>This row is written by TWO different people, which is what earns the
     * column: the owner creates it, and the invitee is the only one who can
     * accept or reject. So after any transition this is collaboratorId, not
     * ownerId.
     *
     * <p>Worth being honest that it's currently DERIVABLE — status != PENDING
     * implies the invitee acted, because that's the only transition the state
     * machine allows. It's recorded anyway because that inference depends on
     * the state machine never gaining another transition, and an audit trail
     * that silently becomes wrong when someone adds a case is a poor audit
     * trail. The whole point of these columns is to state a fact rather than
     * leave it to be reconstructed from assumptions elsewhere in the code.
     *
     * <p>No createdBy here either: a collaboration is only ever created by its
     * owner, so it would restate ownerId. Nullable for the same reason as
     * Note.lastModifiedBy — rows written before Step 10 have no value, and
     * ddl-auto cannot add a NOT NULL column to a populated table.
     */
    @LastModifiedBy
    @Column(name = "last_modified_by")
    private Long lastModifiedBy;
}