package com.notare.feature.collaboration.event;

/**
 * An invitee accepted. The person to notify is the OWNER — they sent the
 * invitation and are waiting on it.
 *
 * <p>A separate type from CollaborationRejectedEvent rather than one
 * "responded" event carrying a status. Two distinct facts get two distinct
 * types, so each consumer writes a handler with no branching, and nobody
 * outside this module has to import CollaborationStatus to work out what
 * happened. Event classes are cheap; a switch in every consumer is not.
 */
public record CollaborationAcceptedEvent(
        Long collaborationId,
        Long ownerId,
        Long collaboratorId
) {
}