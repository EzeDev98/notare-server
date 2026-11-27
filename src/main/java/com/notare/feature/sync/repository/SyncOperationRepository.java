package com.notare.feature.sync.repository;

import com.notare.feature.sync.model.SyncOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/** Data access for applied sync operations. */
public interface SyncOperationRepository extends JpaRepository<SyncOperation, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from sync_operations where owner_id = :userId", nativeQuery = true)
    int deleteAllOwnedBy(@Param("userId") Long userId);

    Optional<SyncOperation> findByOwnerIdAndOperationId(Long ownerId, String operationId);

    @Modifying
    @Query("DELETE FROM SyncOperation s WHERE s.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
