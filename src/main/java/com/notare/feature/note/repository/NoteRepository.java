package com.notare.feature.note.repository;

import org.springframework.data.jpa.repository.Modifying;

import com.notare.feature.note.model.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {

    /** Every note this user owns, in the trash or not: what account deletion removes (step 17g). */
    @Query(value = "select id from notes where owner_id = :userId order by id", nativeQuery = true)
    List<Long> findAllIdsByOwnerId(@Param("userId") Long userId);

    /** Notes of other people this user last edited: the edit stays, the name goes (step 17g). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update notes set last_modified_by = null where last_modified_by = :userId", nativeQuery = true)
    int clearLastModifiedBy(@Param("userId") Long userId);


    /**
     * A single active (not soft-deleted) note that belongs to the given owner.
     * Used by: get single note, update, and soft delete.
     */
    Optional<Note> findByIdAndOwnerIdAndDeletedFalse(Long id, Long ownerId);

    /**
     * A page of the owner's active notes.
     * Sorting (pinned first, newest edited first, etc.) is supplied via the
     * Pageable from the service layer, not hard-coded here.
     */
    Page<Note> findByOwnerIdAndDeletedFalse(Long ownerId, Pageable pageable);

    /**
     * Any note that belongs to the given owner, whether active or soft-deleted.
     * Used by: permanent delete (works regardless of current deleted state).
     */
    Optional<Note> findByIdAndOwnerId(Long id, Long ownerId);

    /**
     * An active note by id, WITHOUT an owner filter.
     *
     * <p>Read the previous four signatures, then this one: every other finder
     * here carries ownerId, because until Step 7 ownership was the only way to
     * reach a note and that scoping WAS the authorization. This method has no
     * such guard, so it is only safe behind a caller that has already proved
     * access some other way — the share module, after it has found a grant row.
     * Nothing in the note module's own endpoints uses it.
     *
     * <p>The deleted = false part is kept: a note in the trash is invisible to
     * collaborators, exactly as it is to its owner.
     */
    Optional<Note> findByIdAndDeletedFalse(Long id);

    /**
     * Active notes for a batch of ids, WITHOUT an owner filter — same caution as
     * above.
     *
     * <p>This exists so "notes shared with me" can resolve a page of grants into
     * notes with ONE query instead of one per row. Fetching in a loop would be
     * the classic N+1: 20 shares, 21 queries.
     *
     * <p>The result may be smaller than the input (ids that are soft-deleted or
     * gone simply don't come back) and its order is not guaranteed to match the
     * ids, so callers index the results rather than zipping them positionally.
     */
    List<Note> findByIdInAndDeletedFalse(Collection<Long> ids);

    /**
     * Delta sync: every note of mine touched at or after the cursor.
     *
     * <p>No deleted filter, and that's the point. A soft-deleted note is still
     * a row, so it flows through this query and reaches the client as a
     * tombstone — "you have this cached, drop it". Filtering it out here would
     * leave offline clients holding notes their owner had already trashed.
     *
     * <p>GreaterThanEqual, not GreaterThan. Combined with the caller re-sending
     * a slightly older cursor, this makes the window deliberately overlap: a
     * write that commits just after a cursor is issued would otherwise fall
     * into the gap between two windows and be lost FOREVER, with nothing to
     * indicate anything went missing. Overlap converts silent data loss into
     * occasionally re-sending rows the client already has, which the merge
     * discards. That's the right way to be wrong.
     *
     * <p>The caller MUST sort by (updatedAt, id) rather than updatedAt alone.
     * Timestamps tie — especially on MySQL DATETIME without fractional seconds
     * — and a sort with ties has no stable order, so paging through a window
     * can show a row twice and skip another entirely.
     */
    Page<Note> findByOwnerIdAndUpdatedAtGreaterThanEqual(
            Long ownerId, LocalDateTime since, Pageable pageable);

    /**
     * Resolve a client-generated id to the note it created, if any.
     *
     * <p>The idempotency check for a replayed offline create. Owner-scoped like
     * everything else — a clientId is only unique within one user, and matching
     * globally would let one user's UUID collide with another's.
     */
    Optional<Note> findByOwnerIdAndClientId(Long ownerId, String clientId);

    /** Trashed notes whose purge time has passed, in id order, after the given id (step 17e). */
    @Query("""
            SELECT n.id FROM Note n
            WHERE n.deleted = true
              AND n.purgeAt IS NOT NULL
              AND n.purgeAt < :now
              AND n.id > :afterId
            ORDER BY n.id
            """)
    List<Long> findPurgeDueIds(@Param("now") LocalDateTime now,
                               @Param("afterId") Long afterId,
                               Pageable pageable);

    /**
     * Keyword search over the owner's active notes' content, case-insensitive
     * and partial.
     *
     * <p>Written as explicit JPQL rather than a derived method name so the
     * escaping is visible at the call site: ESCAPE '!' pairs with
     * LikeEscaper, which neutralises % and _ in the term before it ever
     * reaches this query.
     */
    @Query("""
            SELECT n FROM Note n
            WHERE n.ownerId = :ownerId
              AND n.deleted = false
              AND LOWER(n.content) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '!'
            """)
    Page<Note> searchByOwner(@Param("ownerId") Long ownerId,
                             @Param("term") String term,
                             Pageable pageable);
}