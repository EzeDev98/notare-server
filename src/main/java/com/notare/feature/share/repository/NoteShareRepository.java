package com.notare.feature.share.repository;

import org.springframework.data.jpa.repository.Modifying;

import com.notare.feature.share.model.NoteShare;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Finders here follow the same rule as every other repository in the project:
 * the scoping is baked into the QUERY, not applied as an if-check afterwards.
 * A caller who isn't entitled to a row gets an empty Optional and a 404, so the
 * API never confirms that someone else's note exists.
 */
public interface NoteShareRepository extends JpaRepository<NoteShare, Long> {

    /** Every grant this user gave or received (step 17g). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from note_shares where owner_id = :userId or shared_with_user_id = :userId", nativeQuery = true)
    int deleteAllInvolving(@Param("userId") Long userId);


    /**
     * The access check for the recipient path: "do I have a grant on this note,
     * and at what level?" The level is compared with
     * SharePermission.satisfies(...) by the service — this method deliberately
     * doesn't filter by permission, so a READ-only recipient attempting a write
     * can be told 403 rather than 404. They're allowed to know the note exists;
     * they just can't edit it.
     */
    Optional<NoteShare> findByNoteIdAndSharedWithUserId(Long noteId, Long sharedWithUserId);

    /**
     * The owner-side handle on one grant, addressed by (note, recipient) rather
     * than by share id — that's how the API will express it
     * (DELETE /notes/{noteId}/shares/{userId}), and it saves the client having
     * to track an opaque share id it never sees.
     */
    Optional<NoteShare> findByNoteIdAndOwnerIdAndSharedWithUserId(
            Long noteId, Long ownerId, Long sharedWithUserId);

    /** Friendly pre-check before the unique constraint fires on a duplicate share. */
    boolean existsByNoteIdAndSharedWithUserId(Long noteId, Long sharedWithUserId);

    /**
     * "Who is this note shared with?" — owner-scoped, so passing someone else's
     * noteId returns an empty page rather than leaking their collaborator list.
     */
    Page<NoteShare> findByNoteIdAndOwnerId(Long noteId, Long ownerId, Pageable pageable);

    /** "Notes shared with me" — the recipient's collection (7D). */
    Page<NoteShare> findBySharedWithUserId(Long sharedWithUserId, Pageable pageable);

    /**
     * Revoke everything one owner granted to one person. Used in 7F when a
     * collaboration is cancelled: ending the relationship must also end every
     * note access it authorised, or removing a collaborator would leave live
     * grants behind.
     *
     * <p>Derived deletes load the matching rows and delete them one by one, so
     * this needs an active transaction — the caller supplies it. That's fine at
     * these volumes and it lets entity callbacks run; a @Modifying bulk delete
     * would be the move if the row counts ever got large.
     */
    void deleteByOwnerIdAndSharedWithUserId(Long ownerId, Long sharedWithUserId);

    /**
     * Drop every grant on a note. Needed when a note is permanently deleted —
     * otherwise its grants outlive it as orphan rows, and a future note reusing
     * that id would inherit them. Wiring this into the note module's permanent
     * delete is part of 7F.
     */
    void deleteByNoteId(Long noteId);

    /**
     * "Notes shared with me", counted and ordered in the database (step 15g, B-13).
     *
     * <p>Joins the note by id so a trashed note drops out of the rows AND the total,
     * and orders by the note's own updatedAt, newest first, with the grant id as a
     * tiebreaker so pages never overlap. The join is written in JPQL, which names the
     * Note entity as text: this module takes no Java dependency on the note module,
     * and the page callers pass in must be unsorted, because the order is fixed here.
     */
    @Query(value = "select s from NoteShare s, Note n "
            + "where n.id = s.noteId and n.deleted = false and s.sharedWithUserId = :userId "
            + "order by n.updatedAt desc, s.id desc",
            countQuery = "select count(s) from NoteShare s, Note n "
            + "where n.id = s.noteId and n.deleted = false and s.sharedWithUserId = :userId")
    Page<NoteShare> findVisibleSharedWithUser(@Param("userId") Long userId, Pageable pageable);
}
