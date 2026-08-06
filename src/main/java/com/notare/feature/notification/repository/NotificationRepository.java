package com.notare.feature.notification.repository;

import com.notare.feature.notification.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Recipient-scoped throughout, same rule as every other repository here: the
 * scoping lives in the query, so someone else's notification comes back empty
 * and surfaces as 404 rather than being filtered out by a forgettable if-check.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** The full list for one user. */
    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);

    /** The unread-only view. IsNull reads as "read_at IS NULL" — no boolean column needed. */
    Page<Notification> findByRecipientIdAndReadAtIsNull(Long recipientId, Pageable pageable);

    /**
     * The badge count. A dedicated count query rather than fetching a page and
     * reading totalElements: this runs on nearly every client poll, and it can
     * be answered straight from the (recipient_id, read_at) index without
     * materialising a single row.
     */
    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    /** One notification, scoped to its owner. Used for mark-as-read and delete. */
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    /**
     * Mark everything unread as read, in one statement.
     *
     * <p>The only bulk UPDATE in the project, and it earns the exception: a user
     * clearing a backlog could touch hundreds of rows, and load-then-save-each
     * would be hundreds of SELECTs and UPDATEs to set one column to one value.
     *
     * <p>Two things a bulk update costs, and both are acceptable here:
     * it bypasses the persistence context (any Notification already loaded in
     * this transaction keeps its stale readAt — nothing in the mark-all flow
     * reads one), and it skips entity callbacks, which is why the timestamp is
     * passed in as a parameter rather than left to auditing.
     *
     * <p>The readAt IS NULL predicate makes it idempotent: calling it twice
     * doesn't rewrite the timestamps of things already read.
     */
    @Modifying
    @Query("""
            UPDATE Notification n
               SET n.readAt = :readAt
             WHERE n.recipientId = :recipientId
               AND n.readAt IS NULL
            """)
    int markAllAsRead(@Param("recipientId") Long recipientId,
                      @Param("readAt") LocalDateTime readAt);
}