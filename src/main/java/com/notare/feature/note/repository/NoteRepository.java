package com.notare.feature.note.repository;

import com.notare.feature.note.model.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {

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
     * A soft-deleted note that belongs to the given owner.
     * Used by: restore deleted note (we must be able to find rows that the
     * "active" finders above deliberately hide).
     */
    Optional<Note> findByIdAndOwnerIdAndDeletedTrue(Long id, Long ownerId);

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
     * note titles with ONE query instead of one per row. Fetching in a loop
     * would be the classic N+1: 20 shares, 21 queries.
     *
     * <p>The result may be smaller than the input (ids that are soft-deleted or
     * gone simply don't come back) and its order is not guaranteed to match the
     * ids, so callers index the results rather than zipping them positionally.
     */
    List<Note> findByIdInAndDeletedFalse(Collection<Long> ids);

    /**
     * Keyword search across the owner's active notes, matching a partial,
     * case-insensitive term against title OR content.
     *
     * This is written as an explicit JPQL @Query rather than a derived method
     * name because the logic is "(title matches OR content matches) AND owner
     * AND not-deleted". A derived name can't express that grouping — it would
     * bind the AND/OR the wrong way, applying the owner/deleted filters to only
     * one side of the OR. The parentheses here are the whole point.
     */
    @Query("""
            SELECT n FROM Note n
            WHERE n.ownerId = :ownerId
              AND n.deleted = false
              AND (
                    LOWER(n.title)   LIKE LOWER(CONCAT('%', :term, '%'))
                 OR LOWER(n.content) LIKE LOWER(CONCAT('%', :term, '%'))
              )
            """)
    Page<Note> searchByOwner(@Param("ownerId") Long ownerId,
                             @Param("term") String term,
                             Pageable pageable);
}