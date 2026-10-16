package com.notare.feature.transcription.repository;

import org.springframework.data.jpa.repository.Modifying;

import com.notare.feature.transcription.enums.TranscriptionStatus;
import com.notare.feature.transcription.model.Transcription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TranscriptionRepository extends JpaRepository<Transcription, Long> {

    /** Transcripts of this user's own clips (step 17g). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from transcriptions where owner_id = :userId", nativeQuery = true)
    int deleteAllOwnedBy(@Param("userId") Long userId);

    /** Transcripts this user asked for on other people's clips: they stay, the name goes (step 17g). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update transcriptions set requested_by = null where requested_by = :userId", nativeQuery = true)
    int clearRequestedBy(@Param("userId") Long userId);


    /** Ownership-scoped fetch by transcription id (get status by job id). */
    Optional<Transcription> findByIdAndOwnerId(Long id, Long ownerId);

    /**
     * The primary lookup: a voice note's transcription, ownership-scoped. Used
     * both to check "does this voice note already have a job?" on submit and to
     * read status when the client polls by voice-note id.
     */
    Optional<Transcription> findByVoiceNoteIdAndOwnerId(Long voiceNoteId, Long ownerId);

    /** The transcriptions of a batch of voice notes, for the sync pull (step 17c). */
    List<Transcription> findByVoiceNoteIdIn(Collection<Long> voiceNoteIds);

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

    /**
     * Jobs left PENDING or PROCESSING past a cutoff — normally because the
     * server restarted or slept mid-job and the in-memory worker never got to
     * finish. Not ownership-scoped: recovery runs for every user's stuck jobs.
     */
    @Query("""
            SELECT transcription FROM Transcription transcription
            WHERE transcription.status IN :statuses
              AND COALESCE(transcription.updatedAt, transcription.createdAt) < :cutoff
            """)
    List<Transcription> findStale(@Param("statuses") List<TranscriptionStatus> statuses,
                                  @Param("cutoff") LocalDateTime cutoff);
}