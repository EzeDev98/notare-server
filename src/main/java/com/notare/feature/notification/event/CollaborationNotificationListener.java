package com.notare.feature.notification.event;

import com.notare.feature.collaboration.event.CollaborationAcceptedEvent;
import com.notare.feature.collaboration.event.CollaborationInvitedEvent;
import com.notare.feature.collaboration.event.CollaborationRejectedEvent;
import com.notare.feature.collaboration.event.CollaborationRemovedEvent;
import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.service.impl.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns Step 6's collaboration events into notifications.
 *
 * <p>All three handlers use AFTER_COMMIT — the opposite of the 7F cleanup
 * listener, for the reason stated there: that one revoked access and had to
 * fail together with its cause, while these are best-effort and must never be
 * able to roll back an invitation. Combined with create()'s REQUIRES_NEW, a
 * notification failure here cannot reach the collaboration transaction at all.
 *
 * <p>Note who gets told, and by whom. Direction flips between the invite and
 * the response, and getting it backwards is the easiest mistake in this file:
 * the person who ACTED is the actor, and the OTHER party is the recipient. An
 * invitation is sent by the owner to the invitee; the answer travels back.
 */
@Component
@RequiredArgsConstructor
public class CollaborationNotificationListener {

    private final NotificationDispatcher dispatcher;

    /** Owner invited someone -> tell the INVITEE, actor is the owner. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCollaborationInvited(CollaborationInvitedEvent event) {
        dispatcher.dispatch(new CreateNotificationCommand(
                event.collaboratorId(),
                NotificationType.COLLABORATION_INVITED,
                event.ownerId(),
                NotificationTargetType.COLLABORATION,
                event.collaborationId(),
                // No label: a collaboration has no name of its own. The client
                // resolves the actor id to a person through user search, which
                // is exactly how it renders every other actor here.
                null));
    }

    /** Invitee accepted -> tell the OWNER, who is the one waiting. Actor is the invitee. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCollaborationAccepted(CollaborationAcceptedEvent event) {
        dispatcher.dispatch(new CreateNotificationCommand(
                event.ownerId(),
                NotificationType.COLLABORATION_ACCEPTED,
                event.collaboratorId(),
                NotificationTargetType.COLLABORATION,
                event.collaborationId(),
                null));
    }

    /** Invitee declined -> tell the OWNER. Actor is the invitee. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCollaborationRejected(CollaborationRejectedEvent event) {
        dispatcher.dispatch(new CreateNotificationCommand(
                event.ownerId(),
                NotificationType.COLLABORATION_REJECTED,
                event.collaboratorId(),
                NotificationTargetType.COLLABORATION,
                event.collaborationId(),
                null));
    }

    /**
     * Owner removed me from their connections -> tell the COLLABORATOR. Actor is the owner. Only for an
     * accepted connection: withdrawing a pending invitation tells no one. No target, because the
     * collaboration row is gone and a link would 404 (step 17i, B-12).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCollaborationRemoved(CollaborationRemovedEvent event) {
        if (!event.wasAccepted()) {
            return;
        }
        dispatcher.dispatch(new CreateNotificationCommand(
                event.collaboratorId(),
                NotificationType.COLLABORATION_REMOVED,
                event.ownerId(),
                null,
                null,
                null));
    }
}
