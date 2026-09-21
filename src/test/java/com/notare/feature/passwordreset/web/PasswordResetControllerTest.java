package com.notare.feature.passwordreset.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.exception.InvalidOtpException;
import com.notare.common.exception.InvalidResetTokenException;
import com.notare.common.exception.PasswordMisMatchException;
import com.notare.feature.auth.validation.OtpCodeValidator;
import com.notare.feature.passwordreset.dto.response.VerifyResetOtpResponse;
import com.notare.feature.passwordreset.service.PasswordResetService;
import com.notare.common.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15h of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - see {@link
 * AuthControllerTest}'s disclaimer (Step 15a);
 * same reasoning. Run via {@code mvn test} before trusting it.
 *
 * <p>All three endpoints are on {@code WebSecurityConfig}'s permit-all list
 * ({@code POST /api/v1/password/**}), so {@code addFilters = false} matches
 * how they're actually reached - unauthenticated, exactly like
 * {@link OtpControllerTest} (Step 15c).
 *
 * <p>{@code VerifyResetOtpRequest.code} carries the same {@code @ValidOtpCode}
 * constraint as {@code VerifyOtpRequest} in Step 15c, for the identical
 * reason: {@code @WebMvcTest} does not component-scan a plain
 * {@code @Component} outside the controller-shaped set, so
 * {@link OtpCodeValidator} (which validates the 6-digit code; it used to depend on OtpProperties for
 * {@code app.otp.length=6}) must be imported explicitly, or the constraint
 * would be silently absent and the malformed-code test below would pass for
 * the wrong reason.
 *
 * <p>Covers: {@code forgot-password} - happy path forwards the request and
 * always returns the same fixed {@link com.notare.common.dto.MessageResponse}
 * regardless of whether the service does anything internally (per the
 * controller's own javadoc on why the wording must never reveal whether an
 * account exists), plus a blank/malformed email rejected by validation before
 * the service runs. {@code verify-reset-otp} - happy path returns the
 * service's reset token; a malformed (wrong-length) code is rejected by
 * validation; {@link InvalidOtpException} from the service surfaces as 400.
 * {@code reset-password} - happy path returns the fixed success message;
 * blank fields rejected by validation -&gt; 400, {@link PasswordMisMatchException}
 * -&gt; 400 and {@link InvalidResetTokenException} -&gt; 401, both via
 * {@code GlobalExceptionHandler} (Step 14a).
 */
@WebMvcTest(
        controllers = PasswordResetController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(OtpCodeValidator.class)
class PasswordResetControllerTest {

    private static final String FORGOT_PASSWORD_URL = "/api/v1/password/forgot-password";
    private static final String VERIFY_RESET_OTP_URL = "/api/v1/password/verify-reset-otp";
    private static final String RESET_PASSWORD_URL = "/api/v1/password/reset-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PasswordResetService passwordResetService;

    // ----------------------------------------------------------------
    // POST /password/forgot-password
    // ----------------------------------------------------------------

    @Test
    void forgotPassword_validEmail_returns200WithTheFixedMessageAndForwardsToTheService() throws Exception {
        doNothing().when(passwordResetService).forgotPassword(any());

        mockMvc.perform(post(FORGOT_PASSWORD_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(emailBody("jdoe@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("A reset code has been sent you."));

        verify(passwordResetService).forgotPassword(any());
    }

    @Test
    void forgotPassword_blankEmail_returns400BeforeReachingTheService() throws Exception {
        mockMvc.perform(post(FORGOT_PASSWORD_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(emailBody(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email address is required."));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void forgotPassword_malformedEmail_returns400BeforeReachingTheService() throws Exception {
        mockMvc.perform(post(FORGOT_PASSWORD_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(emailBody("not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Please provide a valid email address."));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void forgotPassword_returnsTheSameFixedMessage_regardlessOfWhetherTheAccountExists() throws Exception {
        // The controller's own javadoc: the answer must be identical whether
        // or not the email is registered, so the service's internal outcome
        // is never allowed to change the response body.
        doNothing().when(passwordResetService).forgotPassword(any());

        mockMvc.perform(post(FORGOT_PASSWORD_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(emailBody("unregistered@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("A reset code has been sent you."));
    }

    // ----------------------------------------------------------------
    // POST /password/verify-reset-otp
    // ----------------------------------------------------------------

    @Test
    void verifyResetOtp_validRequest_returns200WithTheResetToken() throws Exception {
        when(passwordResetService.verifyResetOtp(any()))
                .thenReturn(new VerifyResetOtpResponse("reset-token-abc"));

        mockMvc.perform(post(VERIFY_RESET_OTP_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(verifyBody("jdoe@example.com", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").value("reset-token-abc"));
    }

    @Test
    void verifyResetOtp_wrongLengthCode_returns400BeforeReachingTheService() throws Exception {
        mockMvc.perform(post(VERIFY_RESET_OTP_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(verifyBody("jdoe@example.com", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("Code must be 6 digits."));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void verifyResetOtp_nonDigitCode_returns400BeforeReachingTheService() throws Exception {
        mockMvc.perform(post(VERIFY_RESET_OTP_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(verifyBody("jdoe@example.com", "12a456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("Code must be 6 digits."));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void verifyResetOtp_blankEmail_returns400BeforeReachingTheService() throws Exception {
        mockMvc.perform(post(VERIFY_RESET_OTP_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(verifyBody("", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email address is required."));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void verifyResetOtp_invalidOrExpiredCode_returns400ViaGlobalExceptionHandler() throws Exception {
        when(passwordResetService.verifyResetOtp(any()))
                .thenThrow(new InvalidOtpException("Invalid or expired code."));

        mockMvc.perform(post(VERIFY_RESET_OTP_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(verifyBody("jdoe@example.com", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid or expired code."));
    }

    // ----------------------------------------------------------------
    // POST /password/reset-password
    // ----------------------------------------------------------------

    @Test
    void resetPassword_validRequest_returns200WithTheFixedMessage() throws Exception {
        doNothing().when(passwordResetService).resetPassword(any());

        mockMvc.perform(post(RESET_PASSWORD_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                resetBody("reset-token-abc", "NewPass123", "NewPass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successfully."));

        verify(passwordResetService).resetPassword(any());
    }

    @Test
    void resetPassword_blankResetToken_returns400BeforeReachingTheService() throws Exception {
        mockMvc.perform(post(RESET_PASSWORD_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                resetBody("", "NewPass123", "NewPass123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.resetToken").value("Reset token is required."));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void resetPassword_weakPassword_returns400BeforeReachingTheService() throws Exception {
        mockMvc.perform(post(RESET_PASSWORD_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                resetBody("reset-token-abc", "weak", "weak"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void resetPassword_passwordsDoNotMatch_returns400ViaGlobalExceptionHandler() throws Exception {
        // The two-field mismatch is a SERVICE-level check (PasswordMisMatchException),
        // not bean validation - both fields individually satisfy @NotBlank/@Pattern,
        // so this reaches PasswordResetServiceImpl before failing.
        org.mockito.Mockito.doThrow(new PasswordMisMatchException("Passwords do not match"))
                .when(passwordResetService).resetPassword(any());

        mockMvc.perform(post(RESET_PASSWORD_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                resetBody("reset-token-abc", "NewPass123", "Different123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Passwords do not match"));
    }

    @Test
    void resetPassword_invalidOrExpiredToken_returns401ViaGlobalExceptionHandler() throws Exception {
        org.mockito.Mockito.doThrow(new InvalidResetTokenException(
                        "Invalid or expired reset link. Please request a new one."))
                .when(passwordResetService).resetPassword(any());

        mockMvc.perform(post(RESET_PASSWORD_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                resetBody("expired-token", "NewPass123", "NewPass123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid or expired reset link. Please request a new one."));
    }

    private Map<String, String> emailBody(String email) {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        return body;
    }

    private Map<String, String> verifyBody(String email, String code) {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("code", code);
        return body;
    }

    private Map<String, String> resetBody(String resetToken, String newPassword, String confirmNewPassword) {
        Map<String, String> body = new HashMap<>();
        body.put("resetToken", resetToken);
        body.put("newPassword", newPassword);
        body.put("confirmNewPassword", confirmNewPassword);
        return body;
    }
}