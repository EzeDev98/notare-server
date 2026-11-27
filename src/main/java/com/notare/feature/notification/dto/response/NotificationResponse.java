package com.notare.feature.notification.dto.response;

import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;

import java.time.LocalDateTime;

/** One notification as the recipient sees it. */
public record NotificationResponse(
        Long id,
        NotificationType type,
        Long actorId,
        NotificationTargetType targetType,
        Long targetId,
        String targetLabel,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
