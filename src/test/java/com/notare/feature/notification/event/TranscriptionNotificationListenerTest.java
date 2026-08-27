package com.notare.feature.notification.event;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.service.impl.NotificationDispatcher;
import com.notare.feature.transcription.event.TranscriptionCompletedEvent;
import com.notare.feature.transcription.event.TranscriptionFailedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Step 12f of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link TranscriptionNotificationListener}'s source and follows this
 * codebase's existing Mockito conventions exactly (see {@code
 * NoteShareCleanupListenerTest} for the pattern of calling a
 * {@code @TransactionalEventListener}-annotated method directly, bypassing
 * Spring's proxying since that's infrastructure wiring, not business logic),
 * but it has not actually been compiled or run. Please run it via
 * {@code mvn test} before trusting it.
 *
 * <p>The {@code fallbackExecution = true} the class's javadoc spends most of
 * its length explaining is itself Spring wiring — untestable at the unit
 * level without a real transactional context — so these tests instead lock
 * in the two behavioral facts that live in the method bodies themselves:
 * <ul>
 *   <li>both handlers dispatch with a {@code null} actor — "nobody did this
 *       to you, the system finished a job on your own file" — unlike every
 *       collaboration/share notification, which always names a human;</li>
 *   <li>{@code onTranscriptionFailed} never forwards {@code
 *       event.errorMessage()} anywhere in the dispatched command. The class's
 *       own javadoc is explicit that the raw exception message can carry
 *       provider internals, file paths, or stack detail and must never reach
 *       the client through this path — so the test constructs the event with
 *       a distinctive, sensitive-looking message and asserts the dispatched
 *       command contains no trace of it (the command's only string-shaped
 *       field, {@code targetLabel}, is null).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TranscriptionNotificationListenerTest {

    private static final Long OWNER_ID = 1L;
    private static final Long VOICE_NOTE_ID = 100L;
    private static final Long TRANSCRIPTION_ID = 500L;

    @Mock
    private NotificationDispatcher dispatcher;
    private TranscriptionNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new TranscriptionNotificationListener(dispatcher);
    }
    @Test
    void onTranscriptionCompleted_notifiesTheOwnerWithNoActorAndPointsAtTheVoiceNote() {
        listener.onTranscriptionCompleted(
                new TranscriptionCompletedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                OWNER_ID,
                NotificationType.TRANSCRIPTION_COMPLETED,
                null,                              // no actor — the system did this, not a person
                NotificationTargetType.VOICE_NOTE,
                VOICE_NOTE_ID,
                null));
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void onTranscriptionFailed_notifiesTheOwnerWithNoActorAndPointsAtTheVoiceNote() {
        listener.onTranscriptionFailed(
                new TranscriptionFailedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID, "engine timed out"));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                OWNER_ID,
                NotificationType.TRANSCRIPTION_FAILED,
                null,
                NotificationTargetType.VOICE_NOTE,
                VOICE_NOTE_ID,
                null));
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void onTranscriptionFailed_neverLeaksTheRawErrorMessageIntoTheDispatchedCommand() {
        // A distinctive, sensitive-looking message that would be an obvious
        // giveaway if it ever leaked into the command's label field.
        String sensitiveMessage = "java.io.FileNotFoundException: /var/data/prod-secrets/audio-cache/100.m4a (Permission denied)";

        listener.onTranscriptionFailed(
                new TranscriptionFailedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID, sensitiveMessage));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                OWNER_ID,
                NotificationType.TRANSCRIPTION_FAILED,
                null,
                NotificationTargetType.VOICE_NOTE,
                VOICE_NOTE_ID,
                null)); // targetLabel is null — the only place the raw message could have leaked
    }

    @Test
    void onTranscriptionFailed_nullErrorMessage_isHandledTheSameWayAsAnyOther() {
        listener.onTranscriptionFailed(
                new TranscriptionFailedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID, null));

        verify(dispatcher).dispatch(new CreateNotificationCommand(
                OWNER_ID,
                NotificationType.TRANSCRIPTION_FAILED,
                null,
                NotificationTargetType.VOICE_NOTE,
                VOICE_NOTE_ID,
                null));
    }
}