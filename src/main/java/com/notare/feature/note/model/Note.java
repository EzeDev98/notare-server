package com.notare.feature.note.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "notes",
        indexes = {
                @Index(name = "idx_notes_owner_id", columnList = "owner_id"),
                @Index(name = "idx_notes_owner_deleted", columnList = "owner_id, deleted")
        }
)
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The id of the User who owns this note.
     * Stored as a plain foreign-key value (not a @ManyToOne User relationship)
     * to keep the note module decoupled from the user module.
     * Never changes once the note is created, hence updatable = false.
     */
    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    /**
     * Nullable on purpose: the spec allows creating an "empty note",
     * so a note may exist before the user has typed a title.
     * Length ceiling is enforced again at the DTO layer in step 1C.
     */
    @Column(length = 255)
    private String title;

    /**
     * TEXT rather than VARCHAR so long notes are not truncated.
     * TEXT is understood by MySQL (dev), PostgreSQL (prod) and H2 (test).
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false)
    private boolean archived;

    /**
     * Soft-delete flag. We keep the row and just mark it, so the note
     * can be restored later (spec: "Restore deleted note").
     */
    @Column(nullable = false)
    private boolean deleted;

    /**
     * When the soft-delete happened. Null while the note is active.
     * Useful later for "empty trash after N days" and for audit.
     */
    private LocalDateTime deletedAt;

    /**
     * Optimistic-locking version. Hibernate increments it on every update
     * and rejects a write whose version is stale. We add the column now;
     * the endpoints that expose conflict handling come in step 2.
     */
    @Version
    private Long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}