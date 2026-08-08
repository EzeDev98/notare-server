package com.notare.feature.collaboration.event;

/**
 * An owner invited someone to collaborate. Published inside the invite
 * transaction.
 *
 * <p>Carries both user ids as well as the collaboration id, so a consumer knows
 * who to tell and who to name without querying this module back. That's the
 * rule for every event added in Step 8: an event is a COMPLETE statement of
 * what happened, not a pointer that makes the listener go and look. A listener
 * that reloads state runs later than the event — potentially much later — and
 * would report the world as it is now rather than as it was.
 */
public record CollaborationInvitedEvent(
        Long collaborationId,
        Long ownerId,
        Long collaboratorId
) {
}