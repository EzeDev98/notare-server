package com.notare.feature.notification.event;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.service.NotificationService;
import com.notare.feature.transcription.event.TranscriptionCompletedEvent;
import com.notare.feature.transcription.event.TranscriptionFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns Step 5's transcription outcomes into notifications.
 *
 * <h2>fallbackExecution = true — the reason this class is separate</h2>
 *
 * These two events are published from TranscriptionWorker, which runs on the
 * async pool with NO surrounding transaction: each repository.save there opens
 * and commits its own short transaction, and the publish happens after.
 *
 * <p>A plain @TransactionalEventListener would therefore NEVER FIRE. Not an
 * error, not a warning — Spring has no transaction to attach the callback to,
 * so it silently discards the event. You'd ship it, see no notifications, and
 * have nothing in the logs to explain why.
 *
 * <p>fallbackExecution = true tells Spring to run the handler immediately when
 * there's no transaction, while keeping the AFTER_COMMIT behaviour when there
 * is one. That matters because the same events could later be published from a
 * transactional context (a synchronous re-transcribe, say), and this handler
 * stays correct either way.
 *
 * <p>The tempting alternative — annotating the worker's process() method
 * @Transactional so a transaction exists — would hold a database connection
 * open across the whole engine call. That's precisely what Step 5's
 * short-transaction design was built to avoid, so the fix would undo an earlier
 * good decision to paper over this one.
 */
@Component
@RequiredArgsConstructor
public class TranscriptionNotificationListener {

    private final NotificationService notificationService;

    /**
     * Transcript is ready. Actor is NULL — nobody did this to you, the system
     * finished a job on your own file. Points at the voice note, which is where
     * the transcript is fetched from.
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onTranscriptionCompleted(TranscriptionCompletedEvent event) {
        notificationService.create(new CreateNotificationCommand(
                event.ownerId(),
                NotificationType.TRANSCRIPTION_COMPLETED,
                null,
                NotificationTargetType.VOICE_NOTE,
                event.voiceNoteId(),
                null));
    }

    /**
     * Transcription failed.
     *
     * <p>event.errorMessage() is deliberately NOT passed through. It comes
     * straight off an exception and can carry provider internals, file paths, or
     * stack detail — none of which a user should see, and some of which
     * shouldn't leave the server at all. It's already in the logs via the
     * worker, and on the Transcription row for support to inspect.
     *
     * <p>The notification says only that it failed. The client's phrasing for
     * TRANSCRIPTION_FAILED is where a helpful, safe message belongs.
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onTranscriptionFailed(TranscriptionFailedEvent event) {
        notificationService.create(new CreateNotificationCommand(
                event.ownerId(),
                NotificationType.TRANSCRIPTION_FAILED,
                null,
                NotificationTargetType.VOICE_NOTE,
                event.voiceNoteId(),
                null));
    }
}