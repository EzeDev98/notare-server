package com.notare.feature.notification.service.impl;

import com.notare.feature.notification.channel.NotificationChannel;
import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Step 12b of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link NotificationDispatcher}'s source and follows this codebase's
 * existing Mockito conventions exactly, but it has not actually been
 * compiled or run. Please run it via {@code mvn test} before trusting it.
 *
 * <p>Covers {@link NotificationDispatcher#dispatch}. The class's entire
 * reason to exist, per its own javadoc, is channel isolation — one failing
 * channel must never abort delivery to the others — so that's the center of
 * gravity here:
 * <ul>
 *   <li>every configured channel is called, in order, with the same command;</li>
 *   <li>a channel that throws is caught and logged, and every channel after
 *       it in the list still runs;</li>
 *   <li>a channel throwing is not surfaced to the caller — {@code dispatch}
 *       never propagates, even when every channel fails;</li>
 *   <li>with zero channels configured (a legitimate state — e.g. all
 *       channels disabled in a profile), dispatch is a no-op rather than an
 *       error.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private final CreateNotificationCommand command = new CreateNotificationCommand(
            1L, NotificationType.NOTE_SHARED, 2L, NotificationTargetType.NOTE, 42L, "Q3 Plan");

    @Mock
    private NotificationChannel inAppChannel;

    @Mock
    private NotificationChannel emailChannel;

    @Test
    void dispatch_callsEveryConfiguredChannelWithTheSameCommand() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(inAppChannel, emailChannel));

        dispatcher.dispatch(command);

        verify(inAppChannel).deliver(command);
        verify(emailChannel).deliver(command);
    }

    @Test
    void dispatch_deliversToChannelsInListOrder() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(inAppChannel, emailChannel));

        dispatcher.dispatch(command);

        InOrder order = inOrder(inAppChannel, emailChannel);
        order.verify(inAppChannel).deliver(command);
        order.verify(emailChannel).deliver(command);
    }

    @Test
    void dispatch_oneChannelThrowing_isCaughtAndDoesNotStopTheRemainingChannels() {
        when(inAppChannel.name()).thenReturn("in-app");
        doThrow(new RuntimeException("db unavailable")).when(inAppChannel).deliver(command);
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(inAppChannel, emailChannel));

        // Must not propagate — the caller is a fire-and-forget listener with
        // nothing useful to do with the exception.
        assertThatCode(() -> dispatcher.dispatch(command)).doesNotThrowAnyException();

        // The failing channel was still attempted exactly once, and the
        // channel after it in the list ran anyway.
        verify(inAppChannel, times(1)).deliver(command);
        verify(emailChannel).deliver(command);
    }

    @Test
    void dispatch_everyChannelThrowing_stillCompletesWithoutPropagating() {
        when(inAppChannel.name()).thenReturn("in-app");
        when(emailChannel.name()).thenReturn("email");
        doThrow(new RuntimeException("db unavailable")).when(inAppChannel).deliver(command);
        doThrow(new IllegalStateException("smtp down")).when(emailChannel).deliver(command);
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(inAppChannel, emailChannel));

        assertThatCode(() -> dispatcher.dispatch(command)).doesNotThrowAnyException();

        verify(inAppChannel).deliver(command);
        verify(emailChannel).deliver(command);
    }

    @Test
    void dispatch_noChannelsConfigured_isANoOp() {
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of());

        assertThatCode(() -> dispatcher.dispatch(command)).doesNotThrowAnyException();

        verifyNoInteractions(inAppChannel, emailChannel);
    }

    @Test
    void dispatch_channelThatSucceeds_isNeverAskedForItsNameOnTheHappyPath() {
        // name() exists purely for the failure log line — a channel that never
        // throws should never need to identify itself.
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(inAppChannel));

        dispatcher.dispatch(command);

        verify(inAppChannel, never()).name();
    }
}