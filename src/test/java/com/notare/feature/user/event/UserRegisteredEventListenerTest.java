package com.notare.feature.user.event;

import com.notare.feature.auth.service.OtpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Step 12g of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito and Spring's {@code @Async}/
 * {@code @TransactionalEventListener} types are unavailable as compile-time
 * dependencies here, and {@code target/classes} only holds this project's
 * own already-compiled classes, not third-party jars. Every test below is
 * reasoned carefully from {@link UserRegisteredEventListener}'s source and
 * follows this codebase's existing Mockito conventions exactly (see
 * {@code NoteShareCleanupListenerTest} for the pattern of calling an
 * event-listener method directly, bypassing Spring's async/transactional
 * proxying since that's infrastructure wiring, not business logic), but it
 * has not actually been compiled or run. Please run it via {@code mvn test}
 * before trusting it.
 *
 * <p>The handler itself is a single line — deliberately so, unlike the
 * notification listeners, this one doesn't build a command or a DTO, it just
 * forwards the registered user's id straight into {@link
 * OtpService#sendOtp(Long)} so the welcome/verification OTP goes out after
 * the registration transaction has actually committed (AFTER_COMMIT), off
 * the request thread (@Async). With that little logic in the method body,
 * the tests cover:
 * <ul>
 *   <li>the happy path — the event's {@code userId} is passed through to
 *       {@code sendOtp} unchanged, and nothing else is touched;</li>
 *   <li>a failure from {@code sendOtp} propagates out of the handler rather
 *       than being swallowed — there is no try/catch in this class, unlike
 *       {@code NotificationDispatcher}, so an OTP-sending failure is visible
 *       (to the async executor's uncaught-exception handling / logs) rather
 *       than silently disappearing.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserRegisteredEventListenerTest {

    private static final Long USER_ID = 1L;

    @Mock
    private OtpService otpService;

    private final UserRegisteredEventListener listener = new UserRegisteredEventListener(otpService);

    @Test
    void onUserRegistered_forwardsTheUserIdToSendOtp() {
        listener.onUserRegistered(new UserRegisteredEvent(USER_ID));

        verify(otpService).sendOtp(USER_ID);
        verifyNoMoreInteractions(otpService);
    }

    @Test
    void onUserRegistered_aFailureFromSendOtp_propagatesRatherThanBeingSwallowed() {
        RuntimeException failure = new RuntimeException("mail provider unavailable");
        doThrow(failure).when(otpService).sendOtp(USER_ID);

        assertThatThrownBy(() -> listener.onUserRegistered(new UserRegisteredEvent(USER_ID)))
                .isSameAs(failure);
    }
}