package com.notare.feature.notification.repository;

import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.model.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository repository;

    private final LocalDateTime base =
            LocalDateTime.of(2026, 1, 1, 12, 0);

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        repository.saveAll(List.of(
                notification(
                        1L,
                        2L,
                        NotificationType.TRANSCRIPTION_COMPLETED,
                        NotificationTargetType.NOTE,
                        10L,
                        "First note",
                        null,
                        base.plusMinutes(1)
                ),
                notification(
                        1L,
                        2L,
                        NotificationType.TRANSCRIPTION_FAILED,
                        NotificationTargetType.NOTE,
                        11L,
                        "Second note",
                        base.plusMinutes(10),
                        base.plusMinutes(2)
                ),
                notification(
                        1L,
                        null,
                        NotificationType.TRANSCRIPTION_COMPLETED,
                        NotificationTargetType.VOICE_NOTE,
                        20L,
                        "Recording",
                        null,
                        base.plusMinutes(3)
                ),
                notification(
                        2L,
                        1L,
                        NotificationType.TRANSCRIPTION_COMPLETED,
                        NotificationTargetType.NOTE,
                        30L,
                        "Other user's note",
                        null,
                        base.plusMinutes(4)
                )
        ));

        repository.flush();
    }

    @Test
    void findByRecipientId_returns_only_recipient_notifications() {
        assertThat(
                repository.findByRecipientId(
                        1L,
                        PageRequest.of(0, 20, Sort.by("id"))
                ).getContent()
        )
                .hasSize(3)
                .allMatch(notification ->
                        notification.getRecipientId().equals(1L));
    }

    @Test
    void findByRecipientIdAndReadAtIsNull_returns_only_unread_notifications() {
        assertThat(
                repository.findByRecipientIdAndReadAtIsNull(
                        1L,
                        PageRequest.of(0, 20, Sort.by("id"))
                ).getContent()
        )
                .hasSize(2)
                .allMatch(notification ->
                        notification.getReadAt() == null);
    }

    @Test
    void countByRecipientIdAndReadAtIsNull_counts_only_unread_for_recipient() {
        assertThat(
                repository.countByRecipientIdAndReadAtIsNull(1L)
        ).isEqualTo(2);

        assertThat(
                repository.countByRecipientIdAndReadAtIsNull(2L)
        ).isEqualTo(1);
    }

    @Test
    void findByIdAndRecipientId_enforces_recipient_scope() {
        Notification notification =
                repository.findByRecipientId(
                        1L,
                        PageRequest.of(0, 20, Sort.by("id"))
                )
                .getContent()
                .get(0);

        assertThat(
                repository.findByIdAndRecipientId(
                        notification.getId(),
                        1L
                )
        ).isPresent();

        assertThat(
                repository.findByIdAndRecipientId(
                        notification.getId(),
                        2L
                )
        ).isEmpty();
    }

    @Test
    void markAllAsRead_updates_only_unread_notifications_for_recipient() {
        LocalDateTime readAt =
                base.plusHours(1);

        int updated =
                repository.markAllAsRead(1L, readAt);

        repository.flush();

        assertThat(updated).isEqualTo(2);

        assertThat(
                repository.countByRecipientIdAndReadAtIsNull(1L)
        ).isZero();

        assertThat(
                repository.countByRecipientIdAndReadAtIsNull(2L)
        ).isEqualTo(1);

        Notification alreadyRead =
                repository.findByRecipientId(
                        1L,
                        PageRequest.of(0, 20, Sort.by("id"))
                )
                .getContent()
                .stream()
                .filter(n -> n.getTargetId().equals(11L))
                .findFirst()
                .orElseThrow();

        assertThat(alreadyRead.getReadAt())
                .isEqualTo(base.plusMinutes(10));
    }

    @Test
    void markAllAsRead_is_idempotent() {
        LocalDateTime readAt = base.plusHours(1);

        assertThat(repository.markAllAsRead(1L, readAt))
                .isEqualTo(2);

        assertThat(repository.markAllAsRead(1L, readAt.plusHours(1)))
                .isZero();
    }

    private static Notification notification(
            Long recipientId,
            Long actorId,
            NotificationType type,
            NotificationTargetType targetType,
            Long targetId,
            String targetLabel,
            LocalDateTime readAt,
            LocalDateTime createdAt
    ) {
        return new Notification(
                null,
                recipientId,
                actorId,
                type,
                targetType,
                targetId,
                targetLabel,
                readAt,
                createdAt
        );
    }
}
