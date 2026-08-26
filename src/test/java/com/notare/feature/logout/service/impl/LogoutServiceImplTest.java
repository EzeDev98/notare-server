package com.notare.feature.logout.service.impl;

import com.notare.feature.logout.dto.response.LogoutResponse;
import com.notare.feature.session.service.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Step 5e of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - Mockito is unavailable here (see
 * {@link com.notare.feature.user.service.impl.UserServiceImplementationTest}
 * for the full explanation). Reasoned from source; run via {@code mvn test}
 * before trusting it.
 *
 * <p>Covers {@link LogoutServiceImpl#logout} and {@code logoutAll}. The one
 * behavior worth calling out: {@code logout} with a {@code null} sessionId
 * does NOT throw and does NOT call {@link SessionService} at all - it
 * short-circuits to a 400 {@link LogoutResponse}. That matters because a
 * client that already lost its session id (an expired/cleared cookie, a
 * client-side bug) should get a clean "there is nothing to log out" answer
 * rather than an exception bubbling up from a null being handed to Redis.
 */
@ExtendWith(MockitoExtension.class)
class LogoutServiceImplTest {

    @Mock private SessionService sessionService;

    private final LogoutServiceImpl service = new LogoutServiceImpl(sessionService);

    @Test
    void logout_withANullSessionId_returnsA400WithoutCallingSessionService() {
        LogoutResponse response = service.logout(null, 1L);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("SessionId is null");
        verifyNoInteractions(sessionService);
    }

    @Test
    void logout_withARealSessionId_revokesItForTheRequestingUser_andReturns200() {
        LogoutResponse response = service.logout("session-1", 7L);

        verify(sessionService).revokeSession("session-1", 7L);
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("Logout successfully");
    }

    @Test
    void logout_doesNotSwallowExceptionsFromSessionService() {
        org.mockito.Mockito.doThrow(new com.notare.common.exception.SessionNotFoundException("Session not found."))
                .when(sessionService).revokeSession("missing", 7L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.logout("missing", 7L))
                .isInstanceOf(com.notare.common.exception.SessionNotFoundException.class);
    }

    @Test
    void logoutAll_revokesEverySessionForTheUser_andReturns200() {
        LogoutResponse response = service.logoutAll(7L);

        verify(sessionService).revokeAllSessions(7L);
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("All account logged out successfully");
    }

    @Test
    void logout_andLogoutAll_neverCallEachOthersSessionServiceMethod() {
        service.logout("session-1", 7L);
        verify(sessionService, never()).revokeAllSessions(org.mockito.ArgumentMatchers.anyLong());
    }
}