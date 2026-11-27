package com.notare.feature.note.repository;

import org.springframework.data.jpa.repository.Modifying;

import com.notare.feature.note.model.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Data access for notes, including AI tidy's queries and conditional saves. */
public interface NoteRepository extends JpaRepository<Note, Long> {

    @Query(value = "select id from notes where owner_id = :userId order by id", nativeQuery = true)
    List<Long> findAllIdsByOwnerId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update notes set last_modified_by = null where last_modified_by = :userId", nativeQuery = true)
    int clearLastModifiedBy(@Param("userId") Long userId);

    Optional<Note> findByIdAndOwnerIdAndDeletedFalse(Long id, Long ownerId);

    Page<Note> findByOwnerIdAndDeletedFalse(Long ownerId, Pageable pageable);

    Optional<Note> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<Note> findByIdAndDeletedFalse(Long id);

    List<Note> findByIdInAndDeletedFalse(Collection<Long> ids);

    Page<Note> findByOwnerIdAndUpdatedAtGreaterThanEqual(
            Long ownerId, LocalDateTime since, Pageable pageable);

    Optional<Note> findByOwnerIdAndClientId(Long ownerId, String clientId);

    @Query("""
            SELECT n.id FROM Note n
            WHERE n.deleted = true
              AND n.purgeAt IS NOT NULL
              AND n.purgeAt < :now
              AND n.id > :afterId
            ORDER BY n.id
            """)
    List<Long> findPurgeDueIds(@Param("now") LocalDateTime now,
                               @Param("afterId") Long afterId,
                               Pageable pageable);

    @Query("""
            SELECT n FROM Note n
            WHERE n.ownerId = :ownerId
              AND n.deleted = false
              AND LOWER(n.content) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '!'
            """)
    Page<Note> searchByOwner(@Param("ownerId") Long ownerId,
                             @Param("term") String term,
                             Pageable pageable);

    @Query("""
            SELECT n.id FROM Note n, User u
            WHERE u.id = n.ownerId
              AND u.aiTidyEnabled = true
              AND n.deleted = false
              AND n.updatedAt < :idleBefore
              AND (n.tidiedVersion IS NULL OR n.version > n.tidiedVersion)
              AND (n.tidyNextAt IS NULL OR n.tidyNextAt <= :now)
              AND n.tidyAttempts < :maxAttempts
            ORDER BY n.updatedAt ASC, n.id ASC
            """)
    List<Long> findTidyCandidateIds(@Param("idleBefore") LocalDateTime idleBefore,
                                    @Param("now") LocalDateTime now,
                                    @Param("maxAttempts") int maxAttempts,
                                    Pageable pageable);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Note n
            SET n.content = :content,
                n.preTidyContent = :previous,
                n.version = n.version + 1,
                n.lastModifiedBy = null,
                n.updatedAt = :now,
                n.tidiedVersion = :version + 1,
                n.aiTitle = :aiTitle,
                n.tidyAttempts = 0,
                n.tidyNextAt = null
            WHERE n.id = :id AND n.version = :version AND n.deleted = false
            """)
    int applyTidy(@Param("id") Long id,
                  @Param("version") Long version,
                  @Param("content") String content,
                  @Param("previous") String previous,
                  @Param("aiTitle") String aiTitle,
                  @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Note n
            SET n.tidiedVersion = :version, n.preTidyContent = null, n.tidyAttempts = 0, n.tidyNextAt = null
            WHERE n.id = :id AND n.version = :version
            """)
    int markTidied(@Param("id") Long id, @Param("version") Long version);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Note n SET n.tidyNextAt = :nextAt WHERE n.id = :id")
    int deferTidy(@Param("id") Long id, @Param("nextAt") LocalDateTime nextAt);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Note n SET n.tidyAttempts = n.tidyAttempts + 1, n.tidyNextAt = :nextAt WHERE n.id = :id")
    int recordTidyFailure(@Param("id") Long id, @Param("nextAt") LocalDateTime nextAt);
}
