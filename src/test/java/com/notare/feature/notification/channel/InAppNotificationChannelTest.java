package com.notare.feature.notification.channel;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** Tests for InAppNotificationChannel. */
@ExtendWith(MockitoExtension.class)
class InAppNotificationChannelTest {

    @Mock
    private NotificationService notificationService;

    private InAppNotificationChannel channel;

    @BeforeEach
    void setUp() {
        channel = new InAppNotificationChannel(notificationService);
    }

    @Test
    void name_returnsTheFixedInAppIdentifier() {
        assertThat(channel.name()).isEqualTo("in-app");
    }

    @Test
    void deliver_passesTheCommandStraightThroughToNotificationServiceCreate() {
        CreateNotificationCommand command = new CreateNotificationCommand(
                1L, NotificationType.TRANSCRIPTION_COMPLETED, 2L, NotificationTargetType.NOTE, 42L, "Q3 Plan");

        channel.deliver(command);

        verify(notificationService).create(command);
    }

    @Test
    void deliver_doesNotCatchAFailureFromNotificationService_letsItPropagate() {
        CreateNotificationCommand command = new CreateNotificationCommand(
                1L, NotificationType.TRANSCRIPTION_COMPLETED, 2L, NotificationTargetType.NOTE, 42L, "Q3 Plan");
        RuntimeException failure = new RuntimeException("constraint violation");
        doThrow(failure).when(notificationService).create(command);

        assertThatThrownBy(() -> channel.deliver(command)).isSameAs(failure);
    }
}
