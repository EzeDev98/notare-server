package com.notare.feature.collaboration.event;

/**
 * An invitee declined. Notify the OWNER. Mirror of CollaborationAcceptedEvent —
 * see that class for why the two aren't one event with a status field.
 */
public record CollaborationRejectedEvent(
        Long collaborationId,
        Long ownerId,
        Long collaboratorId
) {
}