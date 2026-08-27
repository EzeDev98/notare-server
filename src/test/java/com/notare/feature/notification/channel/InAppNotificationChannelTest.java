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

/**
 * Step 12c of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link InAppNotificationChannel}'s source and follows this codebase's
 * existing Mockito conventions exactly, but it has not actually been
 * compiled or run. Please run it via {@code mvn test} before trusting it.
 *
 * <p>The class is deliberately thin — a peer channel, not special-cased
 * infrastructure, per its own javadoc — so there isn't much branching to
 * cover. What's worth locking in:
 * <ul>
 *   <li>{@code name()} returns the fixed, lowercase, hyphenated identifier
 *       the dispatcher's failure log line depends on;</li>
 *   <li>{@code deliver} passes the command straight through to {@code
 *       NotificationService.create} unmodified — no transformation, no
 *       filtering;</li>
 *   <li>whatever {@code create} does (including throwing) is NOT caught
 *       here — isolating failures is the dispatcher's job, not this
 *       channel's, so an exception from the service must propagate
 *       out of {@code deliver} untouched.</li>
 * </ul>
 */
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
                1L, NotificationType.NOTE_SHARED, 2L, NotificationTargetType.NOTE, 42L, "Q3 Plan");

        channel.deliver(command);

        verify(notificationService).create(command);
    }

    @Test
    void deliver_doesNotCatchAFailureFromNotificationService_letsItPropagate() {
        CreateNotificationCommand command = new CreateNotificationCommand(
                1L, NotificationType.NOTE_SHARED, 2L, NotificationTargetType.NOTE, 42L, "Q3 Plan");
        RuntimeException failure = new RuntimeException("constraint violation");
        doThrow(failure).when(notificationService).create(command);

        assertThatThrownBy(() -> channel.deliver(command)).isSameAs(failure);
    }
}