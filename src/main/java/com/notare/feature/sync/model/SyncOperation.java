package com.notare.feature.sync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * A record that one client-submitted operation has already been applied.
 *
 * <p><b>Why this table exists.</b> clientId makes CREATE idempotent, but
 * nothing makes UPDATE idempotent, and updates are the common case. Picture it:
 * the client sends "set note 7 to version 4", the server applies it and bumps
 * the note to version 5, and the response is lost on a flaky connection. The
 * client retries the identical operation. Now version 4 is stale — so the
 * server reports a CONFLICT against the user's own successful write, and the
 * client shows a conflict dialog for a change that was never in conflict with
 * anything.
 *
 * <p>That phantom conflict is worse than a duplicate, because the user can't
 * tell it from a real one. The fix is a ledger: the client stamps each
 * operation with a UUID, and a replay of an id already recorded here replays
 * the RESULT instead of the operation.
 *
 * <p><b>Only successes are recorded.</b> A conflicted or failed operation
 * deliberately leaves no row, because it SHOULD be retried — usually after the
 * client merges and sends a corrected version. Recording failures would make
 * the first failure permanent and turn a recoverable conflict into a dead end.
 * The ledger answers exactly one question: "did this already work?"
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "sync_operations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_sync_operations_owner_operation",
                columnNames = {"owner_id", "operation_id"}
        ),
        indexes = {
                // For the eventual retention sweep — see the note at the bottom.
                @Index(name = "idx_sync_operations_created_at", columnList = "created_at")
        }
)
public class SyncOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Whose operation. Scopes the ledger, so one client's UUID can never match
     * another user's row — and a lookup can't be used to probe for activity on
     * another account.
     */
    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    /**
     * The client-generated id of the operation. Unique per owner; the unique
     * constraint is the real guard against two concurrent replays of the same
     * operation both slipping through the lookup.
     */
    @Column(name = "operation_id", nullable = false, length = 64, updatable = false)
    private String operationId;

    /** The note the operation acted on, so a replay can return the current state of the right row. */
    @Column(name = "note_id", nullable = false, updatable = false)
    private Long noteId;

    /**
     * The note's version immediately AFTER the operation applied.
     *
     * <p>Recorded rather than derived, because by replay time the note may have
     * moved on — the user could have edited it again from another device. This
     * says what THIS operation produced, which is what a replaying client is
     * asking about. Whether the note has since changed is a separate question,
     * answered by delta sync.
     */
    @Column(name = "result_version", nullable = false, updatable = false)
    private Long resultVersion;

    /**
     * When it was applied. Every column here is updatable = false: a ledger
     * entry is a statement about the past and is never edited.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // KNOWN GAP, deliberate: nothing prunes this table yet. It grows with every
    // successful offline operation forever. It needs a retention policy — a
    // scheduled delete of rows older than the longest plausible client offline
    // window (weeks, not months) — which is a Step 9F concern. Recording the
    // timestamp and indexing it now means that sweep is a query, not a
    // migration.
}