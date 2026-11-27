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
import com.notare.support.WebSliceSupport;
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

/** Tests for PasswordResetController. */
@WebSliceSupport
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
        doNothing().when(passwordResetService).forgotPassword(any());

        mockMvc.perform(post(FORGOT_PASSWORD_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(emailBody("unregistered@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("A reset code has been sent you."));
    }

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
