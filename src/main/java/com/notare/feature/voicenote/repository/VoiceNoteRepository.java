package com.notare.feature.voicenote.repository;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.notare.feature.voicenote.model.VoiceNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VoiceNoteRepository extends JpaRepository<VoiceNote, Long> {

    /** Every clip this user uploaded, in their own notes and in other people's, hidden or not (step 17g). */
    @Query(value = "select * from voice_notes where owner_id = :userId", nativeQuery = true)
    List<VoiceNote> findAllByOwnerIdIncludingDeleted(@Param("userId") Long userId);


    /** The clip this owner already stored under that client id, for idempotent upload (step 17b). */
    Optional<VoiceNote> findByOwnerIdAndClientId(Long ownerId, String clientId);

    // The clip queries below are NATIVE on purpose: VoiceNote carries @SQLRestriction("deleted = false"),
    // which would hide exactly the rows these have to reach. They serve the note's life cycle (step 17b).

    /** Every clip of a note, trashed or not: what a permanent delete has to remove. */
    @Query(value = "select * from voice_notes where note_id = :noteId", nativeQuery = true)
    List<VoiceNote> findAllByNoteIdIncludingDeleted(@Param("noteId") Long noteId);

    /** The note went to the trash: hide its clips. updated_at moves, so the next pull reports them. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update voice_notes set deleted = true, deleted_at = :at, updated_at = :at "
            + "where note_id = :noteId and deleted = false",
            nativeQuery = true)
    int softDeleteByNoteId(@Param("noteId") Long noteId, @Param("at") LocalDateTime at);

    /**
     * The note came back from the trash: its clips come back too, except the ones the person
     * deleted on their own (removed).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update voice_notes set deleted = false, deleted_at = null, updated_at = :at "
            + "where note_id = :noteId and deleted = true and removed = false",
            nativeQuery = true)
    int restoreByNoteId(@Param("noteId") Long noteId, @Param("at") LocalDateTime at);

    /**
     * Step 18e: gives a clip that belongs to no note a note to live in. Only touches an owner's own
     * standalone clip, so a repeat or a wrong id changes nothing. updated_at moves for the next pull.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update voice_notes set note_id = :noteId, updated_at = :at "
            + "where id = :id and owner_id = :ownerId and note_id is null", nativeQuery = true)
    int attachStandalone(@Param("id") Long id, @Param("ownerId") Long ownerId,
                         @Param("noteId") Long noteId, @Param("at") LocalDateTime at);

    /** The clip's transcript changed: move its change marker so the next pull carries the new state. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update voice_notes set updated_at = :at where id = :id", nativeQuery = true)
    int touch(@Param("id") Long id, @Param("at") LocalDateTime at);

    /** First sync: the owner's visible clips that belong to a note, in a stable order. */
    Page<VoiceNote> findByOwnerIdAndNoteIdIsNotNullOrderByUpdatedAtAscIdAsc(Long ownerId, Pageable pageable);

    /**
     * Later syncs: every clip of this owner changed since the cursor, hidden ones included, so the
     * other devices hear about a delete. NATIVE for the reason given above. The order is the same
     * (updated_at, id) the note feed uses: paging by number is only correct under a total order.
     */
    @Query(value = "select * from voice_notes where owner_id = :ownerId and note_id is not null "
            + "and updated_at >= :since order by updated_at, id",
            countQuery = "select count(*) from voice_notes where owner_id = :ownerId and note_id is not null "
                    + "and updated_at >= :since",
            nativeQuery = true)
    Page<VoiceNote> findClipChangesSince(@Param("ownerId") Long ownerId,
                                         @Param("since") LocalDateTime since,
                                         Pageable pageable);

    /** Removes one row whether or not it is hidden. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from voice_notes where id = :id", nativeQuery = true)
    void deleteRowById(@Param("id") Long id);


    /**
     * A single voice note scoped to its owner. Used by get-metadata, download,
     * stream and delete — the same "you must supply the owner id" guard that
     * protects text notes, so User A can never reach User B's audio.
     */
    Optional<VoiceNote> findByIdAndOwnerId(Long id, Long ownerId);

    /** A page of the owner's voice notes. */
    Page<VoiceNote> findByOwnerId(Long ownerId, Pageable pageable);

    /** All voice notes the owner has attached to a specific text note. */
    List<VoiceNote> findByOwnerIdAndNoteId(Long ownerId, Long noteId);

    /**
     * Batch resolve by id, ownership unchecked. Backs
     * VoiceNoteService#getAuthorizedVoiceNoteSummaries — same
     * pre-authorized-access rule as the note module's findByIdInAndDeletedFalse:
     * the caller must already have established access (a VoiceShare grant) for
     * every id passed in.
     */
    List<VoiceNote> findByIdIn(Collection<Long> ids);
    /**
     * Every voice note attached to a note, regardless of who among the note's
     * collaborators recorded each one. Unscoped by owner on purpose: a note's
     * attachments can now come from more than one contributor (the note owner
     * and any WRITE collaborator), so a listing scoped to a single owner id
     * would silently hide the others' recordings.
     *
     * <p>Ownership of NOTE ACCESS (does this caller have a NoteShare grant, or
     * do they own the note?) is checked by the caller — SharedNoteService for
     * recipients, NoteService's own ownership check for the note owner's own
     * NoteDetail view. This method itself performs no authorization, same
     * "authorized" naming convention as findByIdIn.
     */
    List<VoiceNote> findByNoteId(Long noteId);
}