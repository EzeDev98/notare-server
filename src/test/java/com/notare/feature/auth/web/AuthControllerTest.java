package com.notare.feature.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.exception.InvalidRefreshTokenException;
import com.notare.common.security.WebSecurityConfigTest;
import com.notare.common.web.ClientIpArgumentResolver;
import com.notare.common.web.UserAgentArgumentResolver;
import com.notare.config.WebMvcConfig;
import com.notare.feature.auth.dto.response.TokenResponse;
import com.notare.feature.auth.service.AuthService;
import com.notare.common.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15a of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - Spring Boot's test slice
 * infrastructure (an embedded servlet container, MockMvc, Mockito) is
 * unavailable as a compile-time/runtime dependency here. Reasoned from
 * source against the app's own {@code spring-boot-starter-parent} version
 * (3.2.5 - see {@code WebSecurityConfig}'s downgrade note), so
 * {@code @MockBean} is used rather than the {@code @MockitoBean} that only
 * exists from Boot 3.4 onward. Run via {@code mvn test} before trusting it.
 *
 * <p>Security is deliberately turned OFF for this slice
 * ({@code addFilters = false}): {@link AuthController} takes no
 * {@code @AuthenticationPrincipal} (refreshing a token is, by design, the one
 * authenticated-feeling endpoint that must work WITHOUT a valid access
 * token - see {@code WebSecurityConfig}'s permit-all list, which includes
 * {@code POST /api/v1/auth/refresh}), so wiring in
 * {@code JwtAuthenticationFilter} and its own three dependencies here would
 * only add noise. Full authenticated-vs-anonymous routing is
 * {@link WebSecurityConfigTest}'s job (Step 4f),
 * already covered.
 *
 * <p>{@link WebMvcConfig} IS imported, deliberately: {@code @WebMvcTest}
 * only auto-detects {@code @Controller}/{@code @ControllerAdvice}/converter/
 * filter-shaped beans, not arbitrary {@code @Configuration} classes, so
 * without this import {@code @ClientIp}/{@code @UserAgent} would silently
 * resolve to {@code null} instead of exercising the real resolvers - which
 * would hide exactly the bug this class exists to catch (are the caller's IP
 * and user agent actually reaching {@link AuthService#refreshToken}?).
 *
 * <p>Covers: a valid request maps to 200 with every {@link TokenResponse}
 * field surfaced in the JSON body, and the resolved client IP / user agent
 * are the exact values passed to the service (both the
 * {@code X-Forwarded-For} path and the direct-connection fallback, per
 * {@link ClientIpArgumentResolver}); a blank refresh token is rejected by
 * bean validation before the service is ever called; and
 * {@link InvalidRefreshTokenException} from the service surfaces as 401 via
 * {@code GlobalExceptionHandler}
 * (Step 14a), which {@code @WebMvcTest} picks up automatically as a
 * {@code @RestControllerAdvice}.
 */
@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebMvcConfig.class, ClientIpArgumentResolver.class, UserAgentArgumentResolver.class})
public class AuthControllerTest {

    private static final String REFRESH_URL = "/api/v1/auth/refresh";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void refresh_validToken_returns200WithTokenResponseBody() throws Exception {
        when(authService.refreshToken(any(), anyString(), anyString()))
                .thenReturn(new TokenResponse("new-access-token", "new-refresh-token", "Bearer", 900L));

        mockMvc.perform(post(REFRESH_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("some-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void refresh_forwardsTheRequestBodyToTheService() throws Exception {
        when(authService.refreshToken(any(), anyString(), anyString()))
                .thenReturn(new TokenResponse("a", "b", "Bearer", 1L));

        mockMvc.perform(post(REFRESH_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("the-exact-token"))))
                .andExpect(status().isOk());

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(
                com.notare.feature.auth.dto.request.RefreshTokenRequest.class);
        verify(authService).refreshToken(requestCaptor.capture(), anyString(), anyString());
        assertThat(requestCaptor.getValue().getRefreshToken()).isEqualTo("the-exact-token");
    }

    @Test
    void refresh_xForwardedForHeaderPresent_thatIpIsPassedToTheService() throws Exception {
        // ClientIpArgumentResolver takes the first, trimmed entry of a
        // comma-separated X-Forwarded-For list ahead of the socket address.
        when(authService.refreshToken(any(), anyString(), anyString()))
                .thenReturn(new TokenResponse("a", "b", "Bearer", 1L));

        mockMvc.perform(post(REFRESH_URL)
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                        .header("User-Agent", "Mozilla/5.0 TestAgent")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("tok"))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> userAgentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
        verify(authService).refreshToken(any(), userAgentCaptor.capture(), ipCaptor.capture());

        assertThat(ipCaptor.getValue()).isEqualTo("203.0.113.7");
        assertThat(userAgentCaptor.getValue()).isEqualTo("Mozilla/5.0 TestAgent");
    }

    @Test
    void refresh_noXForwardedForHeader_fallsBackToTheRemoteAddress() throws Exception {
        when(authService.refreshToken(any(), anyString(), anyString()))
                .thenReturn(new TokenResponse("a", "b", "Bearer", 1L));

        mockMvc.perform(post(REFRESH_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("tok"))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
        verify(authService).refreshToken(any(), anyString(), ipCaptor.capture());

        // MockMvc's default mock request reports 127.0.0.1 as the remote
        // address when nothing else overrides it - asserting non-blank
        // rather than pinning the exact loopback string keeps this from
        // being needlessly brittle to MockMvc internals.
        assertThat(ipCaptor.getValue()).isNotBlank();
    }

    @Test
    void refresh_noUserAgentHeader_fallsBackToUnknown() throws Exception {
        when(authService.refreshToken(any(), anyString(), anyString()))
                .thenReturn(new TokenResponse("a", "b", "Bearer", 1L));

        mockMvc.perform(post(REFRESH_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("tok"))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> userAgentCaptor = ArgumentCaptor.forClass(String.class);
        verify(authService).refreshToken(any(), userAgentCaptor.capture(), anyString());

        assertThat(userAgentCaptor.getValue()).isEqualTo("unknown");
    }

    @Test
    void refresh_blankRefreshToken_returns400BeforeReachingTheService() throws Exception {
        mockMvc.perform(post(REFRESH_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.refreshToken").value("Refresh token is required."));

        verifyNoServiceInteraction();
    }

    @Test
    void refresh_missingRefreshTokenField_returns400BeforeReachingTheService() throws Exception {
        mockMvc.perform(post(REFRESH_URL)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.refreshToken").value("Refresh token is required."));

        verifyNoServiceInteraction();
    }

    @Test
    void refresh_serviceRejectsAnInvalidToken_returns401ViaGlobalExceptionHandler() throws Exception {
        when(authService.refreshToken(any(), anyString(), anyString()))
                .thenThrow(new InvalidRefreshTokenException("This refresh token is invalid or has expired."));

        mockMvc.perform(post(REFRESH_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("expired-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("This refresh token is invalid or has expired."));
    }

    private void verifyNoServiceInteraction() {
        org.mockito.Mockito.verifyNoInteractions(authService);
    }

    private java.util.Map<String, String> requestBody(String refreshToken) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("refreshToken", refreshToken);
        return body;
    }
}