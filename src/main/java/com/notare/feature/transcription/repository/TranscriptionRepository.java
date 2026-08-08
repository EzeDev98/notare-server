package com.notare.feature.transcription.repository;

import com.notare.feature.transcription.enums.TranscriptionStatus;
import com.notare.feature.transcription.model.Transcription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TranscriptionRepository extends JpaRepository<Transcription, Long> {

    /** Ownership-scoped fetch by transcription id (get status by job id). */
    Optional<Transcription> findByIdAndOwnerId(Long id, Long ownerId);

    /**
     * The primary lookup: a voice note's transcription, ownership-scoped. Used
     * both to check "does this voice note already have a job?" on submit and to
     * read status when the client polls by voice-note id.
     */
    Optional<Transcription> findByVoiceNoteIdAndOwnerId(Long voiceNoteId, Long ownerId);

    /**
     * Keyword search over the owner's transcripts, partial and case-insensitive.
     * Scoped to a status (we pass COMPLETED) so we only match transcripts that
     * actually have text — pending/failed jobs have none. The status is bound as
     * a parameter; Hibernate maps the enum through the entity's @Enumerated(STRING).
     */
    @Query("""
            SELECT transcription FROM Transcription transcription
            WHERE transcription.ownerId = :ownerId
              AND transcription.status = :status
              AND LOWER(transcription.transcript) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '!'
            """)
    Page<Transcription> searchByOwnerAndStatus(@Param("ownerId") Long ownerId,
                                               @Param("status") TranscriptionStatus status,
                                               @Param("term") String term,
                                               Pageable pageable);
}