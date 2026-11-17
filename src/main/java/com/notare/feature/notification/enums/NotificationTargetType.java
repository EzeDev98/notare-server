package com.notare.feature.notification.enums;

/**
 * What kind of thing the notification points at, so a client can turn
 * (targetType, targetId) into a route without a switch over every
 * NotificationType.
 *
 * <p>It IS derivable from the type — TRANSCRIPTION_COMPLETED always points at a voice note. It's
 * stored anyway, because deriving it means every client reimplements the same
 * mapping, and each one has to be updated when a type is added. Storing it
 * costs one short column and lets a client handle a type it has never seen:
 * unknown label, but it still knows where the tap goes.
 *
 * <p>Nullable on the entity — a notification doesn't have to point anywhere.
 */
public enum NotificationTargetType {

    /** targetId is a note id. */
    NOTE,

    /** targetId is a voice note id. */
    VOICE_NOTE
}