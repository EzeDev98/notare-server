package com.notare.feature.share.event;

/**
 * Published inside the cancel transaction when an owner withdraws an invitation
 * or removes a collaborator.
 *
 * <p>Carries the two user ids rather than the collaboration id, because by the
 * time a listener runs the Collaboration row is being deleted — an id would
 * point at nothing. The ids are also exactly what a consumer needs, so no
 * listener has to reach back into this module to look anything up.
 *
 * <p>This is the note module's cue that any access granted on the strength of
 * that relationship has to go with it.
 */
public record CollaborationRemovedEvent(Long ownerId, Long collaboratorId) {
}