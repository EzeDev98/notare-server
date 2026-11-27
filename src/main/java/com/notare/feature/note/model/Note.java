package com.notare.feature.note.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
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
                @Index(name = "idx_notes_owner_deleted", columnList = "owner_id, deleted"),
                @Index(name = "idx_notes_purge_at", columnList = "purge_at"),
                // Delta sync: "everything of mine that changed since <cursor>",
                // ordered by updated_at. Without this the sync pull degrades into
                // a full scan of the owner's notes on every poll — and sync polls
                // far more often than a human opens the app.
                @Index(name = "idx_notes_owner_updated_at", columnList = "owner_id, updated_at")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notes_owner_client_id",
                columnNames = {"owner_id", "client_id"}
        )
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
     * The id the CLIENT gave this note before the server had ever seen it.
     *
     * <p>An offline-created note has no server id — the device is holding a note
     * that doesn't exist yet. The client generates a UUID, keeps it as its local
     * key, and sends it when the queue is replayed. The server stores it, so a
     * replayed create can be recognised as one it already handled and answered
     * with the existing note rather than a duplicate.
     *
     * <p>That single column does two jobs: it reconciles the client's temporary
     * id with the server's real one, and it makes create idempotent. Without it,
     * a flaky connection during sync produces silent duplicate notes — the
     * client never got the response, so it retries, and the server has no way to
     * know it's the same note.
     *
     * <p>NULLABLE, because notes created online never have one. That's exactly
     * why the uniqueness lives in a composite constraint rather than on the
     * column: both MySQL and PostgreSQL permit many NULLs in a unique index, so
     * every server-created note coexists happily while (owner, clientId) pairs
     * stay unique. Scoped to the owner, not global, since one user's client can
     * only ever collide with itself.
     *
     * <p>updatable = false: a note's origin is a fact about its creation and
     * reassigning it would break the reconciliation it exists to provide.
     */
    @Column(name = "client_id", length = 64, updatable = false)
    private String clientId;

    /**
     * TEXT rather than VARCHAR so long notes are not truncated.
     * TEXT is understood by MySQL (dev), PostgreSQL (prod) and H2 (test).
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean pinned;

    /**
     * Soft-delete flag. We keep the row and just mark it, so the note
     * can be restored later (spec: "Restore deleted note").
     *
     * <p>Step 9 turns this into a second, unplanned benefit: because a trashed
     * note is still a ROW, delta sync can tell a client "this one is gone"
     * simply by including it. A hard delete would leave nothing to send, and
     * offline clients would keep a note the server no longer has. The tombstone
     * this feature needs already existed.
     */
    @Column(nullable = false)
    private boolean deleted;

    /**
     * When the soft-delete happened. Null while the note is active.
     * Useful later for "empty trash after N days" and for audit.
     */
    private LocalDateTime deletedAt;

    /**
     * When the trash job deletes this note for good (step 17e): deletedAt plus the
     * retention window. Null while the note is active; cleared again on restore.
     */
    private LocalDateTime purgeAt;

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

    /**
     * Who last wrote to this note.
     *
     *
     * <p>There is deliberately NO matching createdBy. A note is only ever
     * created by its owner, through createNote or createWithClientId, both of
     * which take ownerId as the creator. createdBy would restate ownerId
     * forever, and a column that can never disagree with another column is not
     * an audit trail — it's duplication that looks like rigour.
     *
     * <p>NULLABLE, and it has to be. Every note written before Step 10 has no
     * value for this, and ddl-auto cannot add a NOT NULL column to a table that
     * already has rows. Null here means "written before auditing recorded who",
     * which is unambiguous precisely because SecurityAuditorAware never returns
     * empty — see SystemAuditor.
     *
     * <p>Reads SYSTEM_AUDITOR (0) for anything the system wrote with no
     * principal on the thread. No current path does that to a note, but the
     * value is possible and a client rendering this must handle it rather than
     * showing "user 0".
     */
    @LastModifiedBy
    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    /** The version AI tidy produced. A note is picked again only once version moves past it. */
    @Column(name = "tidied_version")
    private Long tidiedVersion;

    /** The first line AI tidy wrote. Regenerated only while the first line still equals it. */
    @Column(name = "ai_title")
    private String aiTitle;

    /** The content before the last tidy, for "Restore original". */
    @Column(name = "pre_tidy_content", columnDefinition = "TEXT")
    private String preTidyContent;

    /** Failed tidy runs in a row. The note is left alone once this reaches the max. */
    @Column(name = "tidy_attempts", nullable = false)
    private int tidyAttempts;

    /** Backoff: the note is not picked before this time. */
    @Column(name = "tidy_next_at")
    private LocalDateTime tidyNextAt;
}