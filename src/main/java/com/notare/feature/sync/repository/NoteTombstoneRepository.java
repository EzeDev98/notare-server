package com.notare.feature.sync.repository;

import com.notare.feature.sync.model.NoteTombstone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Data access for note tombstones. */
public interface NoteTombstoneRepository extends JpaRepository<NoteTombstone, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from note_tombstones where owner_id = :userId", nativeQuery = true)
    int deleteAllOwnedBy(@Param("userId") Long userId);

    List<NoteTombstone> findByOwnerIdAndDeletedAtGreaterThanEqual(Long ownerId, LocalDateTime since);

    @Modifying
    @Query("DELETE FROM NoteTombstone t WHERE t.deletedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
