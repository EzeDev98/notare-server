package com.notare.feature.notification.mapper;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.dto.response.NotificationResponse;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.model.Notification;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for NotificationMapper. */
class NotificationMapperTest {

    private final NotificationMapper mapper = new NotificationMapper();

    @Test
    void toEntity_mapsEveryFieldFromTheCommandWithoutTransposingIds() {
        CreateNotificationCommand command = new CreateNotificationCommand(
                100L, NotificationType.TRANSCRIPTION_COMPLETED, 200L, NotificationTargetType.NOTE, 300L, "My Note");

        Notification entity = mapper.toEntity(command);

        assertThat(entity.getRecipientId()).isEqualTo(100L);
        assertThat(entity.getType()).isEqualTo(NotificationType.TRANSCRIPTION_COMPLETED);
        assertThat(entity.getActorId()).isEqualTo(200L);
        assertThat(entity.getTargetType()).isEqualTo(NotificationTargetType.NOTE);
        assertThat(entity.getTargetId()).isEqualTo(300L);
        assertThat(entity.getTargetLabel()).isEqualTo("My Note");
    }

    @Test
    void toEntity_alwaysLeavesReadAtNull() {
        CreateNotificationCommand command = new CreateNotificationCommand(
                100L, NotificationType.TRANSCRIPTION_COMPLETED, null, null, null, null);

        Notification entity = mapper.toEntity(command);

        assertThat(entity.getReadAt()).isNull();
    }

    @Test
    void toEntity_toleratesNullActorAndNullTarget() {
        CreateNotificationCommand command = new CreateNotificationCommand(
                100L, NotificationType.TRANSCRIPTION_FAILED, null, null, null, null);

        Notification entity = mapper.toEntity(command);

        assertThat(entity.getActorId()).isNull();
        assertThat(entity.getTargetType()).isNull();
        assertThat(entity.getTargetId()).isNull();
        assertThat(entity.getTargetLabel()).isNull();
    }

    @Test
    void toResponse_mapsEveryFieldFromTheEntityWithoutTransposingIds() {
        Notification entity = new Notification();
        entity.setId(1L);
        entity.setType(NotificationType.TRANSCRIPTION_COMPLETED);
        entity.setActorId(200L);
        entity.setTargetType(NotificationTargetType.VOICE_NOTE);
        entity.setTargetId(300L);
        entity.setTargetLabel("memo.m4a");
        entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));

        NotificationResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.actorId()).isEqualTo(200L);
        assertThat(response.targetId()).isEqualTo(300L);
        assertThat(response.targetType()).isEqualTo(NotificationTargetType.VOICE_NOTE);
        assertThat(response.targetLabel()).isEqualTo("memo.m4a");
    }

    @Test
    void toResponse_carriesReadAtThrough() {
        Notification entity = new Notification();
        entity.setType(NotificationType.TRANSCRIPTION_FAILED);
        LocalDateTime readAt = LocalDateTime.of(2026, 1, 3, 9, 0);
        entity.setReadAt(readAt);

        NotificationResponse response = mapper.toResponse(entity);

        assertThat(response.readAt()).isEqualTo(readAt);
    }

    @Test
    void toResponse_unreadNotificationHasNullReadAt() {
        Notification entity = new Notification();
        entity.setType(NotificationType.TRANSCRIPTION_FAILED);

        NotificationResponse response = mapper.toResponse(entity);

        assertThat(response.readAt()).isNull();
    }
}
