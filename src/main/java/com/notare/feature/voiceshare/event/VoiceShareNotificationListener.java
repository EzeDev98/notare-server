package com.notare.feature.voiceshare.event;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.service.impl.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns the voiceshare module's sharing events into notifications. Mirrors
 * {@link com.notare.feature.share.event.ShareNotificationListener} exactly:
 * the recipient is the person whose access changed, the actor is always the
 * voice note's owner, all three fire AFTER_COMMIT (a notification about a
 * grant that got rolled back would be worse than none), and this is the
 * first publisher to target NotificationTargetType.VOICE_NOTE.
 */
@Component
@RequiredArgsConstructor
public class VoiceShareNotificationListener {

    private final NotificationDispatcher dispatcher;

    /** A voice note was shared with me. Links straight to it, which I can now open. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoiceNoteShared(VoiceNoteSharedEvent event) {
        dispatcher.dispatch(new CreateNotificationCommand(
                event.sharedWithUserId(),
                NotificationType.VOICE_NOTE_SHARED,
                event.ownerId(),
                NotificationTargetType.VOICE_NOTE,
                event.voiceNoteId(),
                // The filename as it was at the moment of sharing — same
                // snapshot reasoning as ShareNotificationListener#onNoteShared.
                event.voiceNoteTitle()));
    }

    /**
     * My access level changed. Same deliberate limitation as
     * ShareNotificationListener#onNoteSharePermissionChanged: the event
     * carries both old and new permission, and neither is stored — the
     * Notification entity has nowhere to put them yet.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoiceSharePermissionChanged(VoiceSharePermissionChangedEvent event) {
        dispatcher.dispatch(new CreateNotificationCommand(
                event.sharedWithUserId(),
                NotificationType.VOICE_NOTE_SHARE_PERMISSION_CHANGED,
                event.ownerId(),
                NotificationTargetType.VOICE_NOTE,
                event.voiceNoteId(),
                event.voiceNoteTitle()));
    }

    /**
     * My access was withdrawn. Target left NULL, same reasoning as
     * ShareNotificationListener#onNoteShareRevoked: by the time this is read
     * the grant is gone, so a link would 404, and a dead link reads as a bug
     * rather than the intended "you can't get to this anymore". The filename
     * label is what makes the notification useful at all here.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoiceShareRevoked(VoiceShareRevokedEvent event) {
        dispatcher.dispatch(new CreateNotificationCommand(
                event.sharedWithUserId(),
                NotificationType.VOICE_NOTE_SHARE_REVOKED,
                event.ownerId(),
                null,
                null,
                event.voiceNoteTitle()));
    }
}
