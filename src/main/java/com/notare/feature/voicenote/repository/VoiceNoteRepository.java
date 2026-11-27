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

/** Data access for voice clips. */
public interface VoiceNoteRepository extends JpaRepository<VoiceNote, Long> {

    @Query(value = "select * from voice_notes where owner_id = :userId", nativeQuery = true)
    List<VoiceNote> findAllByOwnerIdIncludingDeleted(@Param("userId") Long userId);

    Optional<VoiceNote> findByOwnerIdAndClientId(Long ownerId, String clientId);

    @Query(value = "select * from voice_notes where note_id = :noteId", nativeQuery = true)
    List<VoiceNote> findAllByNoteIdIncludingDeleted(@Param("noteId") Long noteId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update voice_notes set note_id = :noteId, updated_at = :at "
            + "where id = :id and owner_id = :ownerId and note_id is null", nativeQuery = true)
    int attachStandalone(@Param("id") Long id, @Param("ownerId") Long ownerId,
                         @Param("noteId") Long noteId, @Param("at") LocalDateTime at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update voice_notes set updated_at = :at where id = :id", nativeQuery = true)
    int touch(@Param("id") Long id, @Param("at") LocalDateTime at);

    Page<VoiceNote> findByOwnerIdAndNoteIdIsNotNullOrderByUpdatedAtAscIdAsc(Long ownerId, Pageable pageable);

    @Query(value = "select * from voice_notes where owner_id = :ownerId and note_id is not null "
            + "and updated_at >= :since order by updated_at, id",
            countQuery = "select count(*) from voice_notes where owner_id = :ownerId and note_id is not null "
                    + "and updated_at >= :since",
            nativeQuery = true)
    Page<VoiceNote> findClipChangesSince(@Param("ownerId") Long ownerId,
                                         @Param("since") LocalDateTime since,
                                         Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from voice_notes where id = :id", nativeQuery = true)
    void deleteRowById(@Param("id") Long id);

    Optional<VoiceNote> findByIdAndOwnerId(Long id, Long ownerId);

    Page<VoiceNote> findByOwnerId(Long ownerId, Pageable pageable);

    List<VoiceNote> findByOwnerIdAndNoteId(Long ownerId, Long noteId);

    List<VoiceNote> findByIdIn(Collection<Long> ids);
    List<VoiceNote> findByNoteId(Long noteId);
}
