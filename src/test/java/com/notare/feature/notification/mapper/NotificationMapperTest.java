package com.notare.feature.notification.mapper;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.dto.response.NotificationResponse;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.model.Notification;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3d of the coverage roadmap.
 *
 * <p>Covers {@link NotificationMapper#toEntity} and {@link
 * NotificationMapper#toResponse}. Both directions carry four same-typed
 * {@code Long} fields (recipientId, actorId, targetId, and the entity's own
 * id) in close proximity, which is exactly the kind of shape a transposition
 * bug hides in - each is asserted against a distinct value so a swap
 * anywhere in the chain would fail loudly. Also locks in the one piece of
 * real logic here: {@code toEntity} deliberately leaves {@code readAt} null
 * rather than accepting or defaulting it, since "born unread" is the whole
 * point (per the mapper's own doc comment).
 */
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
        // A notification is born unread - that's expressed by ABSENCE, not a
        // flag, per the mapper's own doc comment. Nothing in the command even
        // offers a readAt to copy; this locks in that toEntity doesn't
        // default it to anything else either (e.g. LocalDateTime.now()).
        CreateNotificationCommand command = new CreateNotificationCommand(
                100L, NotificationType.TRANSCRIPTION_COMPLETED, null, null, null, null);

        Notification entity = mapper.toEntity(command);

        assertThat(entity.getReadAt()).isNull();
    }

    @Test
    void toEntity_toleratesNullActorAndNullTarget() {
        // System-generated events (a finished transcription) have no actor
        // and sometimes no target - both must pass through as null, not
        // throw.
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
        // readAt never set - the "unread" state.

        NotificationResponse response = mapper.toResponse(entity);

        assertThat(response.readAt()).isNull();
    }
}