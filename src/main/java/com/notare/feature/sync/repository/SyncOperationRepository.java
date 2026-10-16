package com.notare.feature.sync.repository;

import com.notare.feature.sync.model.SyncOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SyncOperationRepository extends JpaRepository<SyncOperation, Long> {

    /** The operation ledger of this user (step 17g). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from sync_operations where owner_id = :userId", nativeQuery = true)
    int deleteAllOwnedBy(@Param("userId") Long userId);


    /**
     * "Have I already applied this operation?" — the first thing the batch
     * handler asks about every incoming operation.
     *
     * <p>Owner-scoped, same rule as everywhere else. Returns the ledger entry
     * rather than a boolean because a hit needs the noteId and version to
     * reconstruct the original result; a bare exists would answer the question
     * and then force a second query to act on the answer.
     */
    Optional<SyncOperation> findByOwnerIdAndOperationId(Long ownerId, String operationId);

    /**
     * Retention sweep: drop ledger entries older than the cutoff.
     *
     * <p>A bulk delete rather than load-and-delete, for the same reason
     * markAllAsRead is a bulk update — this could touch a very large number of
     * rows and there is nothing to do with the entities.
     *
     * <p>Choosing the cutoff is a real trade-off, not a tuning knob. Too short
     * and a client that was offline longer than the window replays operations
     * whose ledger entries have been swept, reintroducing exactly the phantom
     * conflicts this table prevents. Too long and the table grows without
     * bound. The window has to exceed the longest plausible offline period.
     */
    @Modifying
    @Query("DELETE FROM SyncOperation s WHERE s.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}