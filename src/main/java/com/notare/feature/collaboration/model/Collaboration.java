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
}