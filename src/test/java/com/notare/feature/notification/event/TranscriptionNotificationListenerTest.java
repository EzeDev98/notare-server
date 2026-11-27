package com.notare.feature.notification.event;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.service.NotificationService;
import com.notare.feature.transcription.event.TranscriptionCompletedEvent;
import com.notare.feature.transcription.event.TranscriptionFailedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/** Tests for TranscriptionNotificationListener. */
@ExtendWith(MockitoExtension.class)
class TranscriptionNotificationListenerTest {

    private static final Long OWNER_ID = 1L;
    private static final Long VOICE_NOTE_ID = 100L;
    private static final Long TRANSCRIPTION_ID = 500L;

    @Mock
    private NotificationService notificationService;
    private TranscriptionNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new TranscriptionNotificationListener(notificationService);
    }
    @Test
    void onTranscriptionCompleted_notifiesTheOwnerWithNoActorAndPointsAtTheVoiceNote() {
        listener.onTranscriptionCompleted(
                new TranscriptionCompletedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID));

        verify(notificationService).create(new CreateNotificationCommand(
                OWNER_ID,
                NotificationType.TRANSCRIPTION_COMPLETED,
                null,
                NotificationTargetType.VOICE_NOTE,
                VOICE_NOTE_ID,
                null));
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void onTranscriptionFailed_notifiesTheOwnerWithNoActorAndPointsAtTheVoiceNote() {
        listener.onTranscriptionFailed(
                new TranscriptionFailedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID, "engine timed out"));

        verify(notificationService).create(new CreateNotificationCommand(
                OWNER_ID,
                NotificationType.TRANSCRIPTION_FAILED,
                null,
                NotificationTargetType.VOICE_NOTE,
                VOICE_NOTE_ID,
                null));
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void onTranscriptionFailed_neverLeaksTheRawErrorMessageIntoTheDispatchedCommand() {
        String sensitiveMessage = "java.io.FileNotFoundException: /var/data/prod-secrets/audio-cache/100.m4a (Permission denied)";

        listener.onTranscriptionFailed(
                new TranscriptionFailedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID, sensitiveMessage));

        verify(notificationService).create(new CreateNotificationCommand(
                OWNER_ID,
                NotificationType.TRANSCRIPTION_FAILED,
                null,
                NotificationTargetType.VOICE_NOTE,
                VOICE_NOTE_ID,
                null));
    }

    @Test
    void onTranscriptionFailed_nullErrorMessage_isHandledTheSameWayAsAnyOther() {
        listener.onTranscriptionFailed(
                new TranscriptionFailedEvent(TRANSCRIPTION_ID, VOICE_NOTE_ID, OWNER_ID, null));

        verify(notificationService).create(new CreateNotificationCommand(
                OWNER_ID,
                NotificationType.TRANSCRIPTION_FAILED,
                null,
                NotificationTargetType.VOICE_NOTE,
                VOICE_NOTE_ID,
                null));
    }
}
