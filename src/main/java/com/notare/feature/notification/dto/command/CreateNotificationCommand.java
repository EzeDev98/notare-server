package com.notare.feature.notification.dto.command;

import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.share.enums.SharePermission;

/**
 * Everything needed to create one notification.
 *
 * <p>A "command", not a "request", and it lives in its own package to keep that
 * distinction visible. Everything in dto/request arrives over the wire as JSON
 * and carries validation annotations because it comes from a client that cannot
 * be trusted. This never touches HTTP — it's built in Java by the listeners in
 * 8D and passed straight to the service. Filing it under dto/request would
 * imply it needs @Valid and could be bound from a request body; neither is
 * true, and notifications are never client-created.
 *
 * <p>A record rather than a six-argument method, because six positional
 * parameters — four of which are Longs and two of which are nullable — is a
 * bug waiting to happen. Named components make a swapped actorId and targetId
 * a compile error instead of a silently wrong row.
 */
public record CreateNotificationCommand(

        /** Who to notify. */
        Long recipientId,

        /** What happened. */
        NotificationType type,

        /** Who caused it. Null for system-generated events (a finished transcription). */
        Long actorId,

        /** What it points at, or null for a notification with no destination. */
        NotificationTargetType targetType,
        Long targetId,

        /** The subject's name AS IT IS NOW — snapshotted onto the row. May be null. */
        String targetLabel,

        /** Step 17i: access before a permission change. Null for every other type. */
        SharePermission previousPermission,

        /** Step 17i: access after a permission change. Null for every other type. */
        SharePermission newPermission
) {

    /** For every notification that carries no permission change. */
    public CreateNotificationCommand(Long recipientId, NotificationType type, Long actorId,
                                     NotificationTargetType targetType, Long targetId, String targetLabel) {
        this(recipientId, type, actorId, targetType, targetId, targetLabel, null, null);
    }
}