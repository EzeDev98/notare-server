package com.notare.feature.notification.dto.response;

import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;

import java.time.LocalDateTime;

/**
 * One notification as the recipient sees it.
 *
 * <p>No recipientId: it's always the caller, so sending it back is noise. No
 * derived {@code read} boolean either — readAt already answers it, and shipping
 * both invites them to disagree. The client's check is
 * {@code readAt === null}.
 *
 * <p>No rendered message string. The server sends the FACTS (type, actor,
 * target, label) and the client composes the sentence. That keeps phrasing,
 * translation, and platform-specific wording out of the database, where they'd
 * be frozen into every historical row. It does mean the client must handle an
 * unfamiliar type gracefully — falling back to a generic line rather than
 * rendering nothing.
 */
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