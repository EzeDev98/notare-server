package com.notare.feature.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.web.ClientIpArgumentResolver;
import com.notare.common.web.UserAgentArgumentResolver;
import com.notare.config.WebMvcConfig;
import com.notare.feature.auth.dto.response.UserLoginResponse;
import com.notare.feature.auth.service.LoginService;
import com.notare.common.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15b of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - see {@link AuthControllerTest}'s
 * disclaimer (Step 15a); same reasoning, same {@code @MockBean} choice for
 * Boot 3.2.5. Run via {@code mvn test} before trusting it.
 *
 * <p>Same shape as {@link AuthControllerTest}: security is off
 * ({@code addFilters = false}) because {@code POST /api/v1/login/login} is
 * itself on {@code WebSecurityConfig}'s permit-all list - logging in cannot
 * require a token you don't have yet - and {@link WebMvcConfig} plus the two
 * resolvers are imported so {@code @ClientIp}/{@code @UserAgent} resolve for
 * real rather than silently going null.
 *
 * <p>Route note: the mapping is {@code @RequestMapping("${app.title}/login")}
 * on the class PLUS {@code @PostMapping("/login")} on the method, so the
 * actual path has "login" twice - {@code /api/v1/login/login}. Confirmed
 * against {@code WebSecurityConfig}'s allowlist entry, which is
 * {@code "/api/v1/login/**"} for exactly this reason.
 *
 * <p>Covers: a valid login maps to 200 with every {@link UserLoginResponse}
 * field surfaced; the resolved client IP / user agent reach
 * {@link LoginService#login}; blank username-or-email / blank password are
 * rejected by bean validation before the service runs; and the three
 * authentication failure modes {@link LoginService} can throw -
 * {@link BadCredentialsException}, {@link DisabledException}, and
 * {@link LockedException} - each surface with the correct status through
 * {@link com.notare.common.exception.GlobalExceptionHandler} (Step 14a).
 */
@WebMvcTest(
        controllers = LoginController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebMvcConfig.class, ClientIpArgumentResolver.class, UserAgentArgumentResolver.class})
class LoginControllerTest {

    private static final String LOGIN_URL = "/api/v1/login/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LoginService loginService;

    @Test
    void login_validCredentials_returns200WithLoginResponseBody() throws Exception {
        when(loginService.login(any(), anyString(), anyString())).thenReturn(
                new UserLoginResponse("access-tok", "refresh-tok", "Bearer", 900L, "NOT-ABCDEF", "jdoe"));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("jdoe", "correct-horse"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-tok"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-tok"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.notarianId").value("NOT-ABCDEF"))
                .andExpect(jsonPath("$.username").value("jdoe"));
    }

    @Test
    void login_forwardsClientIpAndUserAgentToTheService() throws Exception {
        when(loginService.login(any(), anyString(), anyString())).thenReturn(
                new UserLoginResponse("a", "b", "Bearer", 1L, "id", "u"));

        mockMvc.perform(post(LOGIN_URL)
                        .header("X-Forwarded-For", "198.51.100.9")
                        .header("User-Agent", "TestAgent/1.0")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("jdoe", "pw"))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> userAgentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
        verify(loginService).login(any(), userAgentCaptor.capture(), ipCaptor.capture());

        assertThat(ipCaptor.getValue()).isEqualTo("198.51.100.9");
        assertThat(userAgentCaptor.getValue()).isEqualTo("TestAgent/1.0");
    }

    @Test
    void login_blankUsernameOrEmail_returns400BeforeReachingTheService() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("", "pw"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.usernameOrEmail").value("Username or email is required."));

        verifyNoInteractions(loginService);
    }

    @Test
    void login_blankPassword_returns400BeforeReachingTheService() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("jdoe", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Password is required."));

        verifyNoInteractions(loginService);
    }

    @Test
    void login_usernameOrEmailTooLong_returns400WithSizeMessage() throws Exception {
        String tooLong = "a".repeat(101);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody(tooLong, "pw"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.usernameOrEmail")
                        .value("Username or email must not exceed 100 characters."));

        verifyNoInteractions(loginService);
    }

    @Test
    void login_passwordTooLong_returns400WithSizeMessage() throws Exception {
        String tooLong = "a".repeat(65);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("jdoe", tooLong))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password")
                        .value("Password must not exceed 64 characters."));

        verifyNoInteractions(loginService);
    }

    @Test
    void login_wrongPassword_returns401ViaGlobalExceptionHandler() throws Exception {
        when(loginService.login(any(), anyString(), anyString()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("jdoe", "wrong-pw"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username/email or password."));
    }

    @Test
    void login_unverifiedAccount_returns403ViaGlobalExceptionHandler() throws Exception {
        when(loginService.login(any(), anyString(), anyString()))
                .thenThrow(new DisabledException("Account not verified"));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("jdoe", "pw"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Please verify your email before logging in."));
    }

    @Test
    void login_lockedAccount_returns423ViaGlobalExceptionHandler() throws Exception {
        when(loginService.login(any(), anyString(), anyString()))
                .thenThrow(new LockedException("Account locked"));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestBody("jdoe", "pw"))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.detail").value("This account has been locked. Please contact support."));
    }

    private Map<String, String> requestBody(String usernameOrEmail, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("usernameOrEmail", usernameOrEmail);
        body.put("password", password);
        return body;
    }
}