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
import com.notare.support.WebSliceSupport;
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

/** Tests for AuthController. */
@WebSliceSupport
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
