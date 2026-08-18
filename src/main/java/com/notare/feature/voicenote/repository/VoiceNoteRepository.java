package com.notare.feature.voicenote.repository;

import com.notare.feature.voicenote.model.VoiceNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VoiceNoteRepository extends JpaRepository<VoiceNote, Long> {

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