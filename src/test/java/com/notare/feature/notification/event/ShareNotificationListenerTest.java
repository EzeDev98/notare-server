package com.notare.feature.notification.event;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.service.impl.NotificationDispatcher;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.event.NoteSharePermissionChangedEvent;
import com.notare.feature.share.event.NoteShareRevokedEvent;
import com.notare.feature.share.event.NoteSharedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Step 12e of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link ShareNotificationListener}'s source and follows this codebase's
 * existing Mockito conventions exactly (see {@code
 * NoteShareCleanupListenerTest} for the pattern of calling a
 * {@code @TransactionalEventListener}-annotated method directly), but it has
 * not actually been compiled or run. Please run it via {@code mvn test}
 * before trusting it.
 *
 * <p>Covers all three handlers. Recipient and actor are simpler here than in
 * {@code CollaborationNotificationListener} — the recipient is always
 * {@code sharedWithUserId} and the actor is always {@code ownerId}, no
 * direction flip — so the tests instead lock in the two things each
 * handler's own doc comment calls out as deliberate and easy to get wrong:
 * <ul>
 *   <li>{@code onNoteShared} and {@code onNoteSharePermissionChanged} both
 *       target the NOTE with the event's {@code noteId} and carry the
 *       snapshotted {@code noteLabel} — the label the note had AT THAT
 *       MOMENT, not whatever it's called now;</li>
 *   <li>{@code onNoteShareRevoked} target is {@code null} even though the
 *       note id is available on the event — a link to a note the recipient
 *       can no longer open would 404, so the handler deliberately omits
 *       both target fields and relies on the label alone to say WHICH note
 *       was lost.</li>
 * </ul>
 * {@code NoteSharePermissionChangedEvent} carries both the previous and new
 * permission, but the doc comment for the handler is explicit that neither
 * is stored — the Notification entity has nowhere to put them yet — so the
 * dispatched command's shape must not depend on which direction the
 * permission changed.
 */
@ExtendWith(MockitoExtension.class)
class ShareNotificationListenerTest {

    private static final Long NOTE_ID = 42L;
    private static final String NOTE_LABEL = "Q3 Plan";
    private static final Long OWNER_ID = 1L;
    private static final Long RECIPIENT_ID = 2L;
    private static final Long SHARE_ID = 99L;

    @Mock
    private NotificationDispatcher dispatcher;

    private final ShareNotificationListener listener = new ShareNotificationListener(dispatcher);

    @Test
    void onNoteShared_notifiesTheRecipientWithTheOwnerAsActorAndLinksToTheNote() {
        listener.onNoteShared(new NoteSharedEvent(
                SHARE_ID, NOTE_ID, NOTE_LABEL, OWNER_ID, RECIPIENT_ID, SharePermission.READ));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                RECIPIENT_ID,
                NotificationType.NOTE_SHARED,
                OWNER_ID,
                NotificationTargetType.NOTE,
                NOTE_ID,
                NOTE_LABEL));
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void onNoteSharePermissionChanged_promotion_stillDispatchesTheSameCommandShapeAsADemotion() {
        listener.onNoteSharePermissionChanged(new NoteSharePermissionChangedEvent(
                SHARE_ID, NOTE_ID, NOTE_LABEL, OWNER_ID, RECIPIENT_ID,
                SharePermission.READ, SharePermission.WRITE));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                RECIPIENT_ID,
                NotificationType.NOTE_SHARE_PERMISSION_CHANGED,
                OWNER_ID,
                NotificationTargetType.NOTE,
                NOTE_ID,
                NOTE_LABEL));
    }

    @Test
    void onNoteSharePermissionChanged_demotion_dispatchesTheIdenticalCommandShape() {
        // Same handler, opposite direction of change — the dispatched command
        // must not differ, since neither old nor new permission is stored.
        listener.onNoteSharePermissionChanged(new NoteSharePermissionChangedEvent(
                SHARE_ID, NOTE_ID, NOTE_LABEL, OWNER_ID, RECIPIENT_ID,
                SharePermission.WRITE, SharePermission.READ));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                RECIPIENT_ID,
                NotificationType.NOTE_SHARE_PERMISSION_CHANGED,
                OWNER_ID,
                NotificationTargetType.NOTE,
                NOTE_ID,
                NOTE_LABEL));
    }

    @Test
    void onNoteShareRevoked_omitsTheTargetEntirely_relyingOnTheLabelAloneToIdentifyTheNote() {
        listener.onNoteShareRevoked(new NoteShareRevokedEvent(NOTE_ID, NOTE_LABEL, OWNER_ID, RECIPIENT_ID));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                RECIPIENT_ID,
                NotificationType.NOTE_SHARE_REVOKED,
                OWNER_ID,
                null,   // no target type — a link would 404 once the grant is gone
                null,   // no target id, for the same reason
                NOTE_LABEL));
        verifyNoMoreInteractions(dispatcher);
    }
}