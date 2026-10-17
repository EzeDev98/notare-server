package com.notare.feature.notification.event;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.service.impl.NotificationDispatcher;
import com.notare.feature.share.event.NoteSharePermissionChangedEvent;
import com.notare.feature.share.event.NoteShareRevokedEvent;
import com.notare.feature.share.event.NoteSharedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns Step 7's sharing events into notifications. The recipient is the same
 * in all three — the person whose access changed — and the actor is always the
 * note's owner.
 */
@Component
@RequiredArgsConstructor
public class ShareNotificationListener {

    private final NotificationDispatcher dispatcher;

    /** A note was shared with me. Links straight to the note, which I can now open. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoteShared(NoteSharedEvent event) {
        dispatcher.dispatch(new CreateNotificationCommand(
                event.sharedWithUserId(),
                NotificationType.NOTE_SHARED,
                event.ownerId(),
                NotificationTargetType.NOTE,
                event.noteId(),
                // The label as it was at the moment of sharing — see the
                // Notification entity for why this is stored rather than joined.
                event.noteLabel()));
    }

    /**
     * My access level changed. Both the old and the new permission are stored on the notification
     * (step 17i, B-12), so the client can say Viewer or Editor and tell a promotion from a demotion.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoteSharePermissionChanged(NoteSharePermissionChangedEvent event) {
        dispatcher.dispatch(new CreateNotificationCommand(
                event.sharedWithUserId(),
                NotificationType.NOTE_SHARE_PERMISSION_CHANGED,
                event.ownerId(),
                NotificationTargetType.NOTE,
                event.noteId(),
                event.noteLabel(),
                event.previousPermission(),
                event.newPermission()));
    }

    /**
     * My access was withdrawn.
     *
     * <p>Target is left NULL even though the note id is right there. By the time
     * this notification is read the grant is gone, so a link would resolve to a
     * 404 — and a dead link is worse than no link: it reads as a bug rather than
     * as the intended "you can't get to this anymore".
     *
     * <p>The label is what makes the notification useful at all here. It's the
     * only way the recipient learns WHICH note they lost, since they can no
     * longer fetch it to find out.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoteShareRevoked(NoteShareRevokedEvent event) {
        if (event.left()) {
            return;
        }
        dispatcher.dispatch(new CreateNotificationCommand(
                event.sharedWithUserId(),
                NotificationType.NOTE_SHARE_REVOKED,
                event.ownerId(),
                null,
                null,
                event.noteLabel()));
    }
}