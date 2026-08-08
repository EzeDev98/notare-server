package com.notare.feature.notification.mapper;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.dto.response.NotificationResponse;
import com.notare.feature.notification.model.Notification;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper, @Component, same as every other module here.
 */
@Component
public class NotificationMapper {

    /**
     * Command -> new entity. readAt is deliberately left null: a notification
     * is born unread, and that's expressed by absence rather than by setting a
     * flag. createdAt is filled by auditing on persist.
     */
    public Notification toEntity(CreateNotificationCommand command) {
        Notification notification = new Notification();
        notification.setRecipientId(command.recipientId());
        notification.setType(command.type());
        notification.setActorId(command.actorId());
        notification.setTargetType(command.targetType());
        notification.setTargetId(command.targetId());
        notification.setTargetLabel(command.targetLabel());
        return notification;
    }

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getActorId(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getTargetLabel(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}