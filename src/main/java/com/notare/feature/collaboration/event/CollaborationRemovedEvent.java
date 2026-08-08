package com.notare.feature.collaboration.event;

/**
 * Published inside the cancel transaction when an owner withdraws an invitation
 * or removes a collaborator.
 *
 * <p>Carries the two user ids rather than the collaboration id, because by the
 * time a listener runs the Collaboration row is being deleted — an id would
 * point at nothing. The ids are also exactly what a consumer needs, so no
 * listener has to reach back into this module to look anything up.
 *
 * <p><b>This class lives in the module that PUBLISHES it, not the module that
 * consumes it.</b> It previously sat in {@code feature.share.event}, which
 * created a cycle: the share module already depends on collaboration (it calls
 * {@code CollaborationService.isAcceptedCollaborator} before granting), and
 * placing the event there made collaboration depend back on share purely to
 * name its own event. Events exist to keep dependencies pointing one way; an
 * event class owned by its listener defeats that.
 *
 * <p>The rule this establishes: <b>an event belongs to its publisher.</b> The
 * publisher states a fact about its own domain; whoever cares subscribes. The
 * publisher must never need to know who that is, and it cannot avoid knowing if
 * the type it constructs lives in the subscriber's package.
 */
public record CollaborationRemovedEvent(Long ownerId, Long collaboratorId) {
}