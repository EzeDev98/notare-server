package com.notare.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.dto.PagedResponse;
import com.notare.feature.auth.service.LoginService;
import com.notare.feature.auth.web.LoginController;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.note.web.NoteController;
import com.notare.feature.session.service.SessionService;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.user.service.impl.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 5 of the test-suite fix plan.
 *
 * <p>{@link WebSecurityConfigTest} deliberately only covers
 * {@code corsConfigurationSource()}, called directly with no Spring context.
 * Everything else in {@link WebSecurityConfig} - which endpoint sits behind
 * which auth rule, {@code maliciousProbesMatcher()}'s blocklist, whether
 * {@code /error} and {@code /actuator/health} are really reachable
 * unauthenticated, and whether {@link JwtAuthenticationFilter} actually
 * authenticates a valid token end-to-end - was never exercised anywhere,
 * because every controller slice test (Steps 1-4 of the fix plan) uses
 * {@code excludeFilters}/{@code addFilters = false} specifically to keep the
 * real filter and real security config out of the context.
 *
 * <p>This class does the opposite on purpose: the real
 * {@link JwtAuthenticationFilter} and the real
 * {@link WebSecurityConfig#securityFilterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity)}
 * are both in play. Only the filter's three collaborators -
 * {@link JwtService}, {@link SessionService}, {@link CustomUserDetailsService}
 * - are {@code @MockBean}s, per Step 5B of the fix plan. Two real controllers
 * are included as concrete endpoints to route requests through:
 * {@link NoteController} (behind {@code anyRequest().authenticated()}) and
 * {@link LoginController} (behind an explicit {@code permitAll()}). The
 * remaining {@code permitAll()} rules ({@code /public/**}, {@code /error},
 * {@code /actuator/health}, the springdoc paths) have no controller in this
 * slice, so a request that clears security lands on Spring's default 404 -
 * what matters for those cases is only that the filter chain doesn't answer
 * with 401/403, which would mean it never reached that far.
 */
@WebMvcTest(controllers = {NoteController.class, LoginController.class})
// WebSecurityConfig's constructor needs ProblemDetailAuthenticationEntryPoint and
// ProblemDetailAccessDeniedHandler. Both are plain @Component classes - they don't implement
// any of the marker types (Filter, WebMvcConfigurer, HandlerMethodArgumentResolver, etc.) that
// @WebMvcTest's component scan picks up automatically the way it does for JwtAuthenticationFilter
// (a Filter), so without importing them explicitly the context fails to start with
// NoSuchBeanDefinitionException. Imported as the real classes, not @MockBean, because their
// actual ProblemDetail/JSON output is exactly what several tests below assert on.
@Import({WebSecurityConfig.class, ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class})
@AutoConfigureMockMvc
class WebSecurityFilterChainTest {

    private static final String NOTES_URL = "/api/v1/notes";
    private static final String LOGIN_URL = "/api/v1/login/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NoteService noteService;

    @MockBean
    private LoginService loginService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User sampleUser(long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("jdoe");
        user.setNotarianId("NOT-ABCDEF");
        user.setEmail("jdoe@example.com");
        user.setPassword("hashed");
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }

    /** Stubs the filter's three collaborators so a Bearer token authenticates cleanly as {@code userId}. */
    private void stubValidToken(String token, String sessionId, long userId) {
        UserDetails userDetails = new UserDetail(sampleUser(userId));
        when(jwtService.extractUsername(token)).thenReturn("jdoe");
        when(jwtService.extractSessionId(token)).thenReturn(sessionId);
        when(jwtService.isTokenValid(eq(token), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("jdoe")).thenReturn(userDetails);
        when(sessionService.isSessionActive(sessionId)).thenReturn(true);
    }

    private ResultActions problemJsonStatus(ResultActions actions, int expectedStatus) throws Exception {
        return actions
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // -----------------------------------------------------------------
    // anyRequest().authenticated() — no token at all
    // -----------------------------------------------------------------

    @Test
    void protectedEndpoint_withNoToken_returns401WithProblemJson_notEmpty403() throws Exception {
        problemJsonStatus(mockMvc.perform(get(NOTES_URL)), 401);

        verifyNoInteractions(noteService);
    }

    @Test
    void protectedEndpoint_withMalformedAuthorizationHeader_returns401() throws Exception {
        // No "Bearer " prefix — JwtAuthenticationFilter treats this the same as no header at all
        // and passes the (still-unauthenticated) request straight through to authorization.
        problemJsonStatus(mockMvc.perform(get(NOTES_URL).header("Authorization", "not-a-bearer-token")), 401);

        verifyNoInteractions(noteService);
    }

    // -----------------------------------------------------------------
    // The real JwtAuthenticationFilter, actually authenticating a token —
    // impossible to exercise under Steps 1-4's excludeFilters approach.
    // -----------------------------------------------------------------

    @Test
    void protectedEndpoint_withValidToken_authenticatesAndReachesController() throws Exception {
        stubValidToken("valid-token", "sid-1", 42L);
        when(noteService.listNotes(eq(42L), eq(false), any()))
                .thenReturn(new PagedResponse<NoteSummaryResponse>(List.of(), 0, 20, 0, 0, false, false));

        mockMvc.perform(get(NOTES_URL).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());

        verify(noteService).listNotes(eq(42L), eq(false), any());
        verify(sessionService).touch("sid-1");
    }

    @Test
    void protectedEndpoint_withExpiredToken_returns401ViaRealEntryPoint_notEmpty403() throws Exception {
        // Before ProblemDetailAuthenticationEntryPoint existed, JwtAuthenticationFilter catching
        // this and clearing the context meant an expired/malformed token fell through to
        // anyRequest().authenticated() and came back as an EMPTY 403 — see that class's javadoc.
        // This is the one test in the whole suite that actually drives a real JwtException
        // through the real filter to confirm that no longer happens. JwtAuthenticationFilter's
        // catch clause is `JwtException | UsernameNotFoundException`, and ExpiredJwtException is
        // one of several JwtException subtypes the real jjwt parser can throw here (others include
        // MalformedJwtException, SignatureException) — the filter treats all of them identically,
        // so asserting on the common supertype covers that whole family without coupling this test
        // to jjwt's internal exception-construction API.
        when(jwtService.extractUsername("expired-token"))
                .thenThrow(new JwtException("token expired"));

        problemJsonStatus(mockMvc.perform(get(NOTES_URL).header("Authorization", "Bearer expired-token")), 401);

        verifyNoInteractions(noteService);
    }

    @Test
    void protectedEndpoint_withValidTokenButRevokedSession_returns401() throws Exception {
        // isSessionActive == false is how instant logout/revocation is enforced on an
        // otherwise-stateless access token that hasn't naturally expired yet.
        when(jwtService.extractUsername("logged-out-token")).thenReturn("jdoe");
        when(jwtService.extractSessionId("logged-out-token")).thenReturn("sid-revoked");
        when(jwtService.isTokenValid(eq("logged-out-token"), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("jdoe"))
                .thenReturn(new UserDetail(sampleUser(42L)));
        when(sessionService.isSessionActive("sid-revoked")).thenReturn(false);

        problemJsonStatus(mockMvc.perform(get(NOTES_URL).header("Authorization", "Bearer logged-out-token")), 401);

        verifyNoInteractions(noteService);
        verify(sessionService, never()).touch(anyString());
    }

    // -----------------------------------------------------------------
    // maliciousProbesMatcher().denyAll() — must win over anyRequest().authenticated(),
    // for both anonymous and authenticated callers.
    // -----------------------------------------------------------------

    @Test
    void maliciousProbe_withNoToken_returns401_sameAsAnyOtherUnauthenticatedDeny() throws Exception {
        problemJsonStatus(mockMvc.perform(get(NOTES_URL + "/backup.env")), 401);
    }

    @Test
    void maliciousProbe_withValidAuthenticatedToken_isStillForbidden() throws Exception {
        // The interesting case: an authenticated user is normally let through by
        // anyRequest().authenticated(). denyAll() on the blocked-extension matcher has to be
        // evaluated first in the chain for this to come back 403 instead of reaching the controller.
        stubValidToken("valid-token", "sid-1", 42L);

        problemJsonStatus(mockMvc.perform(get(NOTES_URL + "/shell.php").header("Authorization", "Bearer valid-token")), 403);

        verifyNoInteractions(noteService);
    }

    // -----------------------------------------------------------------
    // Explicit permitAll() rules
    // -----------------------------------------------------------------

    @Test
    void loginEndpoint_isReachableWithNoToken() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequestFixture("jdoe", "password123"));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    Assertions.assertThat(status)
                            .as("permitAll() endpoint must not be blocked by the filter chain")
                            .isNotIn(401, 403);
                });
    }

    @Test
    void publicPath_isReachableWithNoToken_noHandlerButNotBlocked() throws Exception {
        assertNotBlockedBySecurity(get("/public/anything"));
    }

    @Test
    void errorPath_isReachableWithNoToken_notBlocked() throws Exception {
        assertNotBlockedBySecurity(get("/error"));
    }

    @Test
    void actuatorHealth_isReachableWithNoToken_notBlocked() throws Exception {
        assertNotBlockedBySecurity(get("/actuator/health"));
    }

    @Test
    void swaggerUi_isReachableWithNoToken_notBlocked() throws Exception {
        assertNotBlockedBySecurity(get("/swagger-ui.html"));
    }

    @Test
    void apiDocs_isReachableWithNoToken_notBlocked() throws Exception {
        assertNotBlockedBySecurity(get("/v3/api-docs"));
    }

    private void assertNotBlockedBySecurity(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(result -> {
            int status = result.getResponse().getStatus();
            Assertions.assertThat(status)
                    .as("permitAll() path must not be blocked by the filter chain (401/403); "
                            + "a 404 here just means this slice has no controller mapped at that path")
                    .isNotIn(401, 403);
        });
    }

    /** Minimal stand-in so this file doesn't need to import the real LoginRequest DTO's validation constraints. */
    private record LoginRequestFixture(String usernameOrEmail, String password) {
    }
}