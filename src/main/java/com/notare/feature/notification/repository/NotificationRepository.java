package com.notare.feature.notification.repository;

import com.notare.feature.notification.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/** Data access for notifications, always scoped to the recipient. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from notifications where recipient_id = :userId", nativeQuery = true)
    int deleteAllForRecipient(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update notifications set actor_id = null where actor_id = :userId", nativeQuery = true)
    int clearActor(@Param("userId") Long userId);

    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadAtIsNull(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    @Modifying
    @Query("""
            UPDATE Notification n
               SET n.readAt = :readAt
             WHERE n.recipientId = :recipientId
               AND n.readAt IS NULL
            """)
    int markAllAsRead(@Param("recipientId") Long recipientId,
                      @Param("readAt") LocalDateTime readAt);
}
