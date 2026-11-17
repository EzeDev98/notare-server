package com.notare.feature.notification.dto.command;

import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;

/** Everything needed to create one notification. Built in Java by listeners, never bound from HTTP. */
public record CreateNotificationCommand(
        Long recipientId,
        NotificationType type,
        Long actorId,
        NotificationTargetType targetType,
        Long targetId,
        String targetLabel
) {
}
