package com.notare.feature.voiceshare.event;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.service.impl.NotificationDispatcher;
import com.notare.feature.share.enums.SharePermission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Step 12m of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link VoiceShareNotificationListener}'s source and follows this
 * codebase's existing Mockito conventions exactly (see {@code
 * ShareNotificationListenerTest}, which this class's own javadoc says it
 * mirrors exactly), but it has not actually been compiled or run. Please run
 * it via {@code mvn test} before trusting it.
 *
 * <p>Covers all three handlers. Recipient is always {@code sharedWithUserId}
 * and actor is always {@code ownerId} — no direction flip to worry about —
 * so the tests instead lock in the two things the class's own doc comment
 * calls out as deliberate:
 * <ul>
 *   <li>{@code onVoiceNoteShared} and {@code onVoiceSharePermissionChanged}
 *       both target {@link NotificationTargetType#VOICE_NOTE} with the
 *       event's {@code voiceNoteId} and carry the snapshotted
 *       {@code voiceNoteTitle} — this is the first publisher to target
 *       {@code VOICE_NOTE}, so getting the target type right here is the
 *       whole point of the class existing;</li>
 *   <li>{@code onVoiceShareRevoked} target is {@code null} even though the
 *       voice note id is available on the event — a link to a voice note
 *       the recipient can no longer reach would 404, so the handler
 *       deliberately omits both target fields and relies on the title alone
 *       to say which recording was lost.</li>
 * </ul>
 * {@code VoiceSharePermissionChangedEvent} carries both the previous and new
 * permission, but the doc comment for the handler is explicit that neither
 * is stored — same limitation as the note-share equivalent — so the
 * dispatched command's shape must not depend on which direction the
 * permission changed.
 */
@ExtendWith(MockitoExtension.class)
class VoiceShareNotificationListenerTest {

    private static final Long VOICE_NOTE_ID = 200L;
    private static final String VOICE_NOTE_TITLE = "memo.m4a";
    private static final Long OWNER_ID = 1L;
    private static final Long RECIPIENT_ID = 2L;
    private static final Long SHARE_ID = 500L;

    @Mock
    private NotificationDispatcher dispatcher;
    private VoiceShareNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new VoiceShareNotificationListener(dispatcher);
    }
    @Test
    void onVoiceNoteShared_notifiesTheRecipientWithTheOwnerAsActorAndLinksToTheVoiceNote() {
        listener.onVoiceNoteShared(new VoiceNoteSharedEvent(
                SHARE_ID, VOICE_NOTE_ID, VOICE_NOTE_TITLE, OWNER_ID, RECIPIENT_ID, SharePermission.READ));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                RECIPIENT_ID,
                NotificationType.VOICE_NOTE_SHARED,
                OWNER_ID,
                NotificationTargetType.VOICE_NOTE,
                VOICE_NOTE_ID,
                VOICE_NOTE_TITLE));
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void onVoiceSharePermissionChanged_promotion_stillDispatchesTheSameCommandShapeAsADemotion() {
        listener.onVoiceSharePermissionChanged(new VoiceSharePermissionChangedEvent(
                SHARE_ID, VOICE_NOTE_ID, VOICE_NOTE_TITLE, OWNER_ID, RECIPIENT_ID,
                SharePermission.READ, SharePermission.WRITE));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                RECIPIENT_ID,
                NotificationType.VOICE_NOTE_SHARE_PERMISSION_CHANGED,
                OWNER_ID,
                NotificationTargetType.VOICE_NOTE,
                VOICE_NOTE_ID,
                VOICE_NOTE_TITLE,
                SharePermission.READ, SharePermission.WRITE));
    }

    @Test
    void onVoiceSharePermissionChanged_demotion_dispatchesTheIdenticalCommandShape() {
        // Same handler, opposite direction of change — the dispatched command
        // carries the previous and new permission, which is what tells a demotion apart.
        listener.onVoiceSharePermissionChanged(new VoiceSharePermissionChangedEvent(
                SHARE_ID, VOICE_NOTE_ID, VOICE_NOTE_TITLE, OWNER_ID, RECIPIENT_ID,
                SharePermission.WRITE, SharePermission.READ));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                RECIPIENT_ID,
                NotificationType.VOICE_NOTE_SHARE_PERMISSION_CHANGED,
                OWNER_ID,
                NotificationTargetType.VOICE_NOTE,
                VOICE_NOTE_ID,
                VOICE_NOTE_TITLE,
                SharePermission.WRITE, SharePermission.READ));
    }

    @Test
    void onVoiceShareRevoked_omitsTheTargetEntirely_relyingOnTheTitleAloneToIdentifyTheRecording() {
        listener.onVoiceShareRevoked(new VoiceShareRevokedEvent(
                VOICE_NOTE_ID, VOICE_NOTE_TITLE, OWNER_ID, RECIPIENT_ID));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                RECIPIENT_ID,
                NotificationType.VOICE_NOTE_SHARE_REVOKED,
                OWNER_ID,
                null,   // no target type — a link would 404 once the grant is gone
                null,   // no target id, for the same reason
                VOICE_NOTE_TITLE));
        verifyNoMoreInteractions(dispatcher);
    }
}