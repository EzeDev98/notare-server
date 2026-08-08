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
                // The title as it was at the moment of sharing — see the
                // Notification entity for why this is stored rather than joined.
                event.noteTitle()));
    }

    /**
     * My access level changed.
     *
     * <p>The event carries both the old and new permission, and neither is
     * stored: the Notification entity has nowhere to put them. That's a
     * deliberate limitation, not an oversight — the client currently renders
     * "your access to X changed" without saying whether it was a promotion or a
     * demotion, which is genuinely less useful. Fixing it properly means a
     * payload column on the entity (a small JSON blob, or a typed detail table),
     * and that's a bigger decision than Step 8 needs to make. The event already
     * carries the data, so nothing has to be re-plumbed when it happens.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoteSharePermissionChanged(NoteSharePermissionChangedEvent event) {
        dispatcher.dispatch(new CreateNotificationCommand(
                event.sharedWithUserId(),
                NotificationType.NOTE_SHARE_PERMISSION_CHANGED,
                event.ownerId(),
                NotificationTargetType.NOTE,
                event.noteId(),
                event.noteTitle()));
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
        dispatcher.dispatch(new CreateNotificationCommand(
                event.sharedWithUserId(),
                NotificationType.NOTE_SHARE_REVOKED,
                event.ownerId(),
                null,
                null,
                event.noteTitle()));
    }
}