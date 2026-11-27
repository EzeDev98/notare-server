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

/** Turns finished and failed transcriptions into notifications. */
@Component
@RequiredArgsConstructor
public class TranscriptionNotificationListener {

    private final NotificationService notificationService;

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
