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

/** Data access for transcriptions. */
public interface TranscriptionRepository extends JpaRepository<Transcription, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from transcriptions where owner_id = :userId", nativeQuery = true)
    int deleteAllOwnedBy(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update transcriptions set requested_by = null where requested_by = :userId", nativeQuery = true)
    int clearRequestedBy(@Param("userId") Long userId);

    Optional<Transcription> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<Transcription> findByVoiceNoteIdAndOwnerId(Long voiceNoteId, Long ownerId);

    List<Transcription> findByVoiceNoteIdIn(Collection<Long> voiceNoteIds);

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

    @Query("""
            SELECT transcription FROM Transcription transcription
            WHERE transcription.status IN :statuses
              AND COALESCE(transcription.updatedAt, transcription.createdAt) < :cutoff
            """)
    List<Transcription> findStale(@Param("statuses") List<TranscriptionStatus> statuses,
                                  @Param("cutoff") LocalDateTime cutoff);
}
