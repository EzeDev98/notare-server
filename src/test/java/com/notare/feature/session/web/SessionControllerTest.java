package com.notare.feature.session.web;

import com.notare.common.exception.SessionNotFoundException;
import com.notare.common.security.JwtAuthenticationFilter;
import com.notare.feature.session.dto.response.SessionResponse;
import com.notare.feature.session.service.SessionService;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15i of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - see {@link
 * AuthControllerTest}'s disclaimer (Step 15a);
 * same reasoning. Run via {@code mvn test} before trusting it.
 *
 * <p>Authenticated principal is supplied the same way as
 * {@link CollaborationControllerTest}
 * (Step 15d) - directly on {@code SecurityContextHolder} in the test thread,
 * with {@code addFilters = false} keeping the real
 * {@code JwtAuthenticationFilter} chain out of this slice.
 *
 * <p>{@code GET /session/sessions} needs the same stand-in as
 * {@link LogoutControllerTest} (Step 15e) for
 * the request ATTRIBUTE {@code JwtAuthenticationFilter} would ordinarily
 * populate - the current session id, read here via
 * {@code @RequestAttribute(required = false)} so the service can mark which
 * row in the list is the caller's own session. A
 * {@link RequestPostProcessor} sets it directly on the underlying mock
 * request; its absence (the {@code required = false} branch) is exercised
 * too, since a currently-valid access token with no session attribute at all
 * is a real state to be in - e.g. a token minted by a path that doesn't set
 * one - and the controller must still forward {@code null} rather than fail
 * to resolve the argument.
 *
 * <p>Covers: {@code GET /session/sessions} - the authenticated user's id and
 * the resolved current-session id both reach the service, each row of the
 * returned list serialises with all seven {@link SessionResponse} fields
 * (including which one is flagged {@code current}), an empty list serialises
 * as {@code []} rather than an error, and the missing-attribute case forwards
 * {@code null} rather than blowing up. {@code DELETE
 * /session/sessions/{sessionId}} - 204 on success, delegating with the
 * authenticated user's id (never anything from the body, since there is
 * none) as the requesting user; and {@link SessionNotFoundException} (the
 * session doesn't exist, or exists but belongs to someone else - the service
 * doesn't distinguish which) -&gt; 404 via
 * {@code GlobalExceptionHandler} (Step 14a).
 */
@WebMvcTest(
        controllers = SessionController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class SessionControllerTest {

    private static final String SESSIONS_URL = "/api/v1/session/sessions";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionService sessionService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("jdoe");
        user.setNotarianId("NOT-ABCDEF");
        user.setEmail("jdoe@example.com");
        user.setPassword("hashed");
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);

        UserDetail userDetail = new UserDetail(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetail, null, userDetail.getAuthorities()));
    }

    /** Stands in for what {@code JwtAuthenticationFilter} would normally set. */
    private RequestPostProcessor withSessionId(String sessionId) {
        return request -> {
            request.setAttribute(JwtAuthenticationFilter.SESSION_ID_ATTRIBUTE, sessionId);
            return request;
        };
    }

    private SessionResponse sampleSession(String sessionId, boolean current) {
        Instant now = Instant.now();
        return new SessionResponse(
                sessionId, "Mozilla/5.0 TestAgent", "203.0.113.7", now, now, now.plusSeconds(3600), current);
    }

    // ----------------------------------------------------------------
    // GET /session/sessions
    // ----------------------------------------------------------------

    @Test
    void listSessions_validRequest_forwardsCallerIdAndCurrentSessionIdToTheService() throws Exception {
        authenticateAs(1L);
        when(sessionService.listSessions(eq(1L), eq("session-abc")))
                .thenReturn(List.of(sampleSession("session-abc", true)));

        mockMvc.perform(get(SESSIONS_URL).with(withSessionId("session-abc")))
                .andExpect(status().isOk());

        verify(sessionService).listSessions(1L, "session-abc");
    }

    @Test
    void listSessions_returnsEachSessionWithAllFieldsIncludingWhichIsCurrent() throws Exception {
        authenticateAs(1L);
        when(sessionService.listSessions(eq(1L), anyString()))
                .thenReturn(List.of(
                        sampleSession("session-abc", true),
                        sampleSession("session-def", false)));

        mockMvc.perform(get(SESSIONS_URL).with(withSessionId("session-abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].sessionId").value("session-abc"))
                .andExpect(jsonPath("$[0].userAgent").value("Mozilla/5.0 TestAgent"))
                .andExpect(jsonPath("$[0].ipAddress").value("203.0.113.7"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[1].sessionId").value("session-def"))
                .andExpect(jsonPath("$[1].current").value(false));
    }

    @Test
    void listSessions_noActiveSessions_returnsAnEmptyArray() throws Exception {
        authenticateAs(1L);
        when(sessionService.listSessions(eq(1L), anyString())).thenReturn(List.of());

        mockMvc.perform(get(SESSIONS_URL).with(withSessionId("session-abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void listSessions_noSessionIdAttribute_forwardsNullRatherThanFailing() throws Exception {
        // JwtAuthenticationFilter is excluded from this slice, so with no
        // withSessionId(...) post-processor the @RequestAttribute(required =
        // false) resolves to null - a real state (a valid access token whose
        // request never had the attribute set), not an error case.
        authenticateAs(1L);
        when(sessionService.listSessions(eq(1L), isNull()))
                .thenReturn(List.of(sampleSession("session-abc", false)));

        mockMvc.perform(get(SESSIONS_URL))
                .andExpect(status().isOk());

        verify(sessionService).listSessions(1L, null);
    }

    @Test
    void listSessions_isKeyedOffTheAuthenticatedPrincipalNotAnythingElse() throws Exception {
        authenticateAs(77L);
        when(sessionService.listSessions(anyLong(), anyString())).thenReturn(List.of());

        mockMvc.perform(get(SESSIONS_URL).with(withSessionId("session-xyz")))
                .andExpect(status().isOk());

        verify(sessionService).listSessions(77L, "session-xyz");
    }

    // ----------------------------------------------------------------
    // DELETE /session/sessions/{sessionId}
    // ----------------------------------------------------------------

    @Test
    void revokeSession_validRequest_returns204AndDelegatesWithTheCallersId() throws Exception {
        authenticateAs(1L);
        doNothing().when(sessionService).revokeSession("session-abc", 1L);

        mockMvc.perform(delete(SESSIONS_URL + "/session-abc"))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());

        ArgumentCaptor<String> sessionIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(sessionService).revokeSession(sessionIdCaptor.capture(), userIdCaptor.capture());

        assertThat(sessionIdCaptor.getValue()).isEqualTo("session-abc");
        assertThat(userIdCaptor.getValue()).isEqualTo(1L);
    }

    @Test
    void revokeSession_unknownOrNotOwnedByCaller_returns404ViaGlobalExceptionHandler() throws Exception {
        // SessionService#revokeSession doesn't distinguish "doesn't exist"
        // from "exists but belongs to someone else" - both are the same 404,
        // deliberately not leaking which one it was.
        authenticateAs(1L);
        org.mockito.Mockito.doThrow(new SessionNotFoundException("Session not found."))
                .when(sessionService).revokeSession("someone-elses-session", 1L);

        mockMvc.perform(delete(SESSIONS_URL + "/someone-elses-session"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Session not found."));
    }

    @Test
    void revokeSession_usesTheAuthenticatedPrincipalAsRequestingUserNotAnythingFromThePath() throws Exception {
        authenticateAs(42L);
        doNothing().when(sessionService).revokeSession("session-abc", 42L);

        mockMvc.perform(delete(SESSIONS_URL + "/session-abc"))
                .andExpect(status().isNoContent());

        verify(sessionService).revokeSession("session-abc", 42L);
    }
}