package com.notare.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.dto.PagedResponse;
import com.notare.feature.auth.service.LoginService;
import com.notare.feature.auth.web.LoginController;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.note.web.NoteController;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.user.service.impl.CustomUserDetailsService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import com.notare.support.WebSliceSupport;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests for WebSecurityFilterChain. */
@WebSliceSupport
@WebMvcTest(controllers = {NoteController.class, LoginController.class})
@Import({WebSecurityConfig.class, ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class})
@AutoConfigureMockMvc
class WebSecurityFilterChainTest {

    private static final String NOTES_URL = "/api/v1/notes";
    private static final String LOGIN_URL = "/api/v1/login/login";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private NoteService noteService;
    @MockBean private LoginService loginService;
    @MockBean private JwtDecoder jwtDecoder;
    @MockBean private CustomUserDetailsService userDetailsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void stubValidToken(String token, long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("jdoe");
        user.setNotarianId("NOT-ABCDEF");
        user.setEmail("jdoe@example.com");
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);

        Jwt jwt = Jwt.withTokenValue(token).header("alg", "none").claim("username", "jdoe")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        when(jwtDecoder.decode(token)).thenReturn(jwt);
        when(userDetailsService.loadUserByUsername("jdoe")).thenReturn(new UserDetail(user));
    }

    private ResultActions problemJsonStatus(ResultActions actions, int expectedStatus) throws Exception {
        return actions
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void protectedEndpoint_withNoToken_returns401WithProblemJson() throws Exception {
        problemJsonStatus(mockMvc.perform(get(NOTES_URL)), 401);
        verifyNoInteractions(noteService);
    }

    @Test
    void protectedEndpoint_withMalformedAuthorizationHeader_returns401() throws Exception {
        problemJsonStatus(mockMvc.perform(get(NOTES_URL).header("Authorization", "not-a-bearer-token")), 401);
        verifyNoInteractions(noteService);
    }

    @Test
    void protectedEndpoint_withValidToken_authenticatesAndReachesController() throws Exception {
        stubValidToken("valid-token", 42L);
        when(noteService.listNotes(eq(42L), eq(false), any()))
                .thenReturn(new PagedResponse<NoteSummaryResponse>(List.of(), 0, 20, 0, 0, false, false));

        mockMvc.perform(get(NOTES_URL).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());

        verify(noteService).listNotes(eq(42L), eq(false), any());
    }

    @Test
    void protectedEndpoint_withRejectedToken_returns401_notEmpty403() throws Exception {
        when(jwtDecoder.decode("expired-token")).thenThrow(new BadJwtException("expired"));

        problemJsonStatus(mockMvc.perform(get(NOTES_URL).header("Authorization", "Bearer expired-token")), 401);

        verifyNoInteractions(noteService);
    }

    @Test
    void maliciousProbe_withNoToken_returns401() throws Exception {
        problemJsonStatus(mockMvc.perform(get(NOTES_URL + "/backup.env")), 401);
    }

    @Test
    void maliciousProbe_withValidToken_isStillForbidden() throws Exception {
        stubValidToken("valid-token", 42L);

        problemJsonStatus(mockMvc.perform(get(NOTES_URL + "/shell.php").header("Authorization", "Bearer valid-token")), 403);

        verifyNoInteractions(noteService);
    }

    @Test
    void loginEndpoint_isReachableWithNoToken() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequestFixture("jdoe", "password123"));

        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(result -> Assertions.assertThat(result.getResponse().getStatus())
                        .as("permitAll() endpoint must not be blocked by the filter chain")
                        .isNotIn(401, 403));
    }

    @Test
    void publicPaths_areReachableWithNoToken() throws Exception {
        assertNotBlockedBySecurity(get("/public/anything"));
        assertNotBlockedBySecurity(get("/error"));
        assertNotBlockedBySecurity(get("/actuator/health"));
        assertNotBlockedBySecurity(get("/swagger-ui.html"));
        assertNotBlockedBySecurity(get("/v3/api-docs"));
    }

    private void assertNotBlockedBySecurity(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(result -> Assertions.assertThat(result.getResponse().getStatus())
                .as("permitAll() path must not be blocked by the filter chain (a 404 just means no controller here)")
                .isNotIn(401, 403));
    }

    private record LoginRequestFixture(String usernameOrEmail, String password) {
    }
}
