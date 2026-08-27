package com.notare.feature.logout.web;

import com.notare.common.security.JwtAuthenticationFilter;
import com.notare.feature.logout.dto.response.LogoutResponse;
import com.notare.feature.logout.service.LogoutService;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15e of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - see {@link
 * AuthControllerTest}'s disclaimer (Step 15a);
 * same reasoning. Run via {@code mvn test} before trusting it.
 *
 * <p>Authenticated principal is supplied the same way as
 * {@link CollaborationControllerTest}
 * (Step 15d) - directly on {@link SecurityContextHolder} in the test thread,
 * with {@code addFilters = false} keeping {@link JwtAuthenticationFilter}
 * itself out of this slice (that filter's own behaviour is Step 4b's job).
 *
 * <p>{@code /logout} also needs the request ATTRIBUTE that
 * {@code JwtAuthenticationFilter} would ordinarily stash - the session id,
 * under {@link JwtAuthenticationFilter#SESSION_ID_ATTRIBUTE}
 * ({@code "sid"}) - since {@link LogoutController#logout} reads it via
 * {@code @RequestAttribute(required = false)}. With the real filter excluded,
 * nothing populates that attribute unless the test does, so a
 * {@link RequestPostProcessor} is used to set it directly on the underlying
 * {@link MockHttpServletRequest} before the request is dispatched - the
 * {@code required = false} means Spring itself won't complain about its
 * absence, but the controller's own null-check (see below) will still fire
 * exactly as it would against a genuinely sessionless request.
 *
 * <p>Both endpoints are unusual among this app's controllers in that they
 * never touch {@code GlobalExceptionHandler}: {@link LogoutServiceImpl}
 * (this class's collaborator, {@link LogoutService} here) never throws - it
 * folds every case, including a null session id, into a
 * {@link LogoutResponse} whose {@code statusCode} the controller echoes
 * verbatim via {@code ResponseEntity.status(...)}. So the controller's own
 * contract to verify is narrower than most: does it forward the right
 * session id and user id, and does it surface whatever status/message pair
 * the service handed back, unmodified?
 *
 * <p>Covers: {@code POST /logout} - a present session id is forwarded
 * together with the authenticated user's id, and the service's 200 response
 * is echoed as the HTTP status with the same JSON body; a missing session id
 * (this controller's {@code required = false} branch) still reaches the
 * service as {@code null} - and it is this class's {@link LogoutServiceImpl}
 * collaborator (not the controller) that turns that into the 400 seen here,
 * per its own null-check. {@code POST /logout/logout-all} - only the
 * authenticated user's id is forwarded and the service's 200 is echoed the
 * same way.
 */
@WebMvcTest(
        controllers = LogoutController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
public class LogoutControllerTest {

    private static final String LOGOUT_URL = "/api/v1/logout/logout";
    private static final String LOGOUT_ALL_URL = "/api/v1/logout/logout-all";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LogoutService logoutService;

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

    // ----------------------------------------------------------------
    // POST /logout/logout
    // ----------------------------------------------------------------

    @Test
    void logout_sessionIdPresent_forwardsSessionIdAndUserIdAndEchoesTheServiceResponse() throws Exception {
        authenticateAs(1L);
        when(logoutService.logout("session-abc", 1L))
                .thenReturn(new LogoutResponse(HttpServletResponse.SC_OK, "Logout successfully"));

        mockMvc.perform(post(LOGOUT_URL).with(withSessionId("session-abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.message").value("Logout successfully"));

        verify(logoutService).logout("session-abc", 1L);
    }

    @Test
    void logout_usesTheAuthenticatedPrincipalsIdNotAnythingElse() throws Exception {
        authenticateAs(42L);
        when(logoutService.logout(eq("session-xyz"), eq(42L)))
                .thenReturn(new LogoutResponse(HttpServletResponse.SC_OK, "Logout successfully"));

        mockMvc.perform(post(LOGOUT_URL).with(withSessionId("session-xyz")))
                .andExpect(status().isOk());

        verify(logoutService).logout("session-xyz", 42L);
    }

    @Test
    void logout_noSessionIdAttribute_stillReachesTheServiceAsNull_andEchoesItsStatus() throws Exception {
        // JwtAuthenticationFilter is excluded from this slice (addFilters =
        // false), so with no withSessionId(...) post-processor the
        // @RequestAttribute(required = false) resolves to null - exactly the
        // "no session on this request" case LogoutServiceImpl's own
        // null-check exists to answer with a 400.
        authenticateAs(1L);
        when(logoutService.logout(eq(null), eq(1L)))
                .thenReturn(new LogoutResponse(HttpServletResponse.SC_BAD_REQUEST, "SessionId is null"));

        mockMvc.perform(post(LOGOUT_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.message").value("SessionId is null"));

        verify(logoutService).logout(null, 1L);
    }

    // ----------------------------------------------------------------
    // POST /logout/logout-all
    // ----------------------------------------------------------------

    @Test
    void logoutAll_validRequest_forwardsTheAuthenticatedUsersIdAndEchoesTheServiceResponse() throws Exception {
        authenticateAs(7L);
        when(logoutService.logoutAll(7L))
                .thenReturn(new LogoutResponse(HttpServletResponse.SC_OK, "All account logged out successfully"));

        mockMvc.perform(post(LOGOUT_ALL_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.message").value("All account logged out successfully"));

        verify(logoutService).logoutAll(7L);
    }

    @Test
    void logoutAll_takesNoBodyOrSessionId_onlyTheAuthenticatedUsersId() throws Exception {
        authenticateAs(3L);
        when(logoutService.logoutAll(3L))
                .thenReturn(new LogoutResponse(HttpServletResponse.SC_OK, "All account logged out successfully"));

        // Even with a session id present on the request, logout-all never
        // reads it - only the LogoutService#logoutAll(Long) overload is called.
        mockMvc.perform(post(LOGOUT_ALL_URL).with(withSessionId("some-session")))
                .andExpect(status().isOk());

        verify(logoutService).logoutAll(3L);
        verifyNoMoreLogoutInteractionsBeyondLogoutAll();
    }

    private void verifyNoMoreLogoutInteractionsBeyondLogoutAll() {
        org.mockito.Mockito.verify(logoutService, org.mockito.Mockito.never())
                .logout(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }
}