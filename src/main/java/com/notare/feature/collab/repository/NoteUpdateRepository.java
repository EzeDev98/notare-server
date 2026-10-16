package com.notare.feature.collab.repository;

import com.notare.feature.collab.model.NoteUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NoteUpdateRepository extends JpaRepository<NoteUpdate, Long> {

    /** Edits this user made to other people's notes stay with the note, credited to its owner (step 17g). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update note_updates set author_id = (select n.owner_id from notes n where n.id = note_updates.note_id) where author_id = :userId and note_id in (select id from notes)", nativeQuery = true)
    int reassignAuthorsToNoteOwners(@Param("userId") Long userId);

    /** Whatever is still left authored by this user, for notes that no longer exist (step 17g). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from note_updates where author_id = :userId", nativeQuery = true)
    int deleteAuthoredBy(@Param("userId") Long userId);


    /** The highest stored seq for the note, or 0 when it has no updates. */
    @Query("select coalesce(max(u.seq), 0L) from NoteUpdate u where u.noteId = :noteId")
    long maxSeq(@Param("noteId") Long noteId);

    /** Updates a client has not seen yet, oldest first. Served by uq_note_updates_note_seq. */
    List<NoteUpdate> findByNoteIdAndSeqGreaterThanOrderBySeqAsc(Long noteId, Long seq);

    /** Compaction: everything up to and including seq is folded into a snapshot. */
    @Modifying
    @Query("delete from NoteUpdate u where u.noteId = :noteId and u.seq <= :seq")
    int deleteUpTo(@Param("noteId") Long noteId, @Param("seq") Long seq);

    /** Permanent delete of the note: all of its updates, in one statement. */
    @Modifying
    @Query("delete from NoteUpdate u where u.noteId = :noteId")
    int deleteAllForNote(@Param("noteId") Long noteId);
}
