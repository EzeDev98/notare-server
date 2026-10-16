package com.notare.feature.sync.repository;

import com.notare.feature.sync.model.NoteTombstone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NoteTombstoneRepository extends JpaRepository<NoteTombstone, Long> {

    /** Tombstones of this user's notes (step 17g). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from note_tombstones where owner_id = :userId", nativeQuery = true)
    int deleteAllOwnedBy(@Param("userId") Long userId);


    /**
     * Tombstones in the client's delta window.
     *
     * <p>Returns a List, not a Page, and that's a deliberate exception to the
     * paging used everywhere else. Tombstones only ever appear in INCREMENTAL
     * windows — a first sync (no cursor) sends none at all, because a client
     * with nothing cached has nothing to evict — so a window holds only what
     * one user permanently deleted since they last synced. That's a handful of
     * bare ids.
     *
     * <p>Paginating them would mean merging two independently paged sources
     * into one response, which is genuinely hard to get right and impossible to
     * page consistently. Not paginating sidesteps it. The cost is a pathological
     * case: a user who permanently deletes thousands of notes while one device
     * is offline gets one large id array. Acceptable, and cheap to notice —
     * see the log line in the pull service.
     */
    List<NoteTombstone> findByOwnerIdAndDeletedAtGreaterThanEqual(Long ownerId, LocalDateTime since);

    /** Retention sweep — see SyncRetentionJob for why the window matters. */
    @Modifying
    @Query("DELETE FROM NoteTombstone t WHERE t.deletedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}