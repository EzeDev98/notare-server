package com.notare.feature.collaboration.event;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.event.CollaborationNotificationListener;
import com.notare.feature.notification.service.impl.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Step 12d of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link CollaborationNotificationListener}'s source and follows this
 * codebase's existing Mockito conventions exactly (see {@code
 * NoteShareCleanupListenerTest} for the pattern of calling a
 * {@code @TransactionalEventListener}-annotated method directly, bypassing
 * Spring's AFTER_COMMIT proxying since that's infrastructure wiring, not
 * business logic), but it has not actually been compiled or run. Please run
 * it via {@code mvn test} before trusting it.
 *
 * <p>Covers all three handlers. The class's own javadoc calls out that
 * direction flips between the invite and the response — "the person who
 * ACTED is the actor, and the OTHER party is the recipient" — and says
 * getting it backwards is the easiest mistake in the file. These tests
 * assert recipient and actor as two SEPARATE fields on the dispatched
 * command, so a regression that swaps them fails clearly rather than being
 * masked by an assertion that only checks "some notification went out":
 * <ul>
 *   <li>{@code onCollaborationInvited}: recipient is the INVITEE
 *       ({@code collaboratorId}), actor is the OWNER;</li>
 *   <li>{@code onCollaborationAccepted}: recipient is the OWNER, actor is
 *       the INVITEE ({@code collaboratorId}) — the reverse of the invite;</li>
 *   <li>{@code onCollaborationRejected}: same reversal as accepted, with the
 *       REJECTED notification type.</li>
 * </ul>
 * All three target a {@code COLLABORATION} with the event's
 * {@code collaborationId} and carry no label, since a collaboration has no
 * name of its own.
 */
@ExtendWith(MockitoExtension.class)
class CollaborationNotificationListenerTest {

    private static final Long COLLABORATION_ID = 7L;
    private static final Long OWNER_ID = 1L;
    private static final Long COLLABORATOR_ID = 2L;

    @Mock
    private NotificationDispatcher dispatcher;

    // Built in @BeforeEach, not as a field initializer: field initializers run
    // during instance construction, which happens BEFORE MockitoExtension
    // injects the @Mock fields (that injection is a postProcessTestInstance
    // callback that runs after construction). Building `listener` as a field
    // initializer freezes it around a still-null dispatcher — and since the
    // field is final, it stays null for the lifetime of the test.
    private CollaborationNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new CollaborationNotificationListener(dispatcher);
    }

    @Test
    void onCollaborationInvited_notifiesTheInviteeWithTheOwnerAsActor() {
        listener.onCollaborationInvited(new CollaborationInvitedEvent(COLLABORATION_ID, OWNER_ID, COLLABORATOR_ID));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                COLLABORATOR_ID,                         // recipient: the invitee
                NotificationType.COLLABORATION_INVITED,
                OWNER_ID,                                 // actor: the owner who invited
                NotificationTargetType.COLLABORATION,
                COLLABORATION_ID,
                null));
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void onCollaborationAccepted_notifiesTheOwnerWithTheInviteeAsActor() {
        listener.onCollaborationAccepted(new CollaborationAcceptedEvent(COLLABORATION_ID, OWNER_ID, COLLABORATOR_ID));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                OWNER_ID,                                  // recipient: the owner, who was waiting
                NotificationType.COLLABORATION_ACCEPTED,
                COLLABORATOR_ID,                            // actor: the invitee who accepted
                NotificationTargetType.COLLABORATION,
                COLLABORATION_ID,
                null));
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void onCollaborationRejected_notifiesTheOwnerWithTheInviteeAsActor() {
        listener.onCollaborationRejected(new CollaborationRejectedEvent(COLLABORATION_ID, OWNER_ID, COLLABORATOR_ID));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                OWNER_ID,                                  // recipient: the owner
                NotificationType.COLLABORATION_REJECTED,
                COLLABORATOR_ID,                            // actor: the invitee who declined
                NotificationTargetType.COLLABORATION,
                COLLABORATION_ID,
                null));
        verifyNoMoreInteractions(dispatcher);
    }
}