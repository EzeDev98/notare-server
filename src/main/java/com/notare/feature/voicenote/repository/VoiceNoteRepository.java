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
}