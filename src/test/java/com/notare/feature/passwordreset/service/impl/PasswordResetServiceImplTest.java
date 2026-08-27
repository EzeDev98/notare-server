package com.notare.feature.passwordreset.service.impl;

import com.notare.common.exception.InvalidOtpException;
import com.notare.common.exception.InvalidResetTokenException;
import com.notare.common.exception.OtpExpiredException;
import com.notare.common.exception.PasswordMisMatchException;
import com.notare.common.security.JwtService;
import com.notare.feature.auth.enums.OtpPurpose;
import com.notare.feature.auth.service.OtpService;
import com.notare.feature.passwordreset.dto.request.ForgotPasswordRequest;
import com.notare.feature.passwordreset.dto.request.ResetPasswordRequest;
import com.notare.feature.passwordreset.dto.request.VerifyResetOtpRequest;
import com.notare.feature.passwordreset.dto.response.VerifyResetOtpResponse;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Step 6b of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - Mockito, Spring Security ({@code
 * PasswordEncoder}), and jjwt ({@code JwtException}) are unavailable as
 * compile-time dependencies here (see {@link
 * UserServiceImplementationTest} for the
 * full explanation). Reasoned carefully from source; run via
 * {@code mvn test} before trusting it.
 *
 * <p>Covers {@link PasswordResetServiceImpl}'s three methods:
 *
 * <ul>
 *   <li>{@code forgotPassword} never reveals whether an email is registered -
 *       an unknown address just logs and returns, no exception, no different
 *       response shape (the controller returns the same 200 either way).</li>
 *   <li>{@code verifyResetOtp} delegates entirely to {@link OtpService},
 *       including its own account-oracle protection ({@code
 *       "Invalid or expired code."} for both an unknown email and, via {@code
 *       OtpService}, a wrong code) - this class adds nothing on top except
 *       minting the reset JWT after a successful verify.</li>
 *   <li>{@code resetPassword} treats a structurally-invalid-but-unexpired
 *       token, an EXPIRED token (jjwt throws {@link JwtException} on parse),
 *       and a token for a since-deleted user all as the SAME generic {@link
 *       InvalidResetTokenException} message - deliberately, so a client can't
 *       distinguish "your link expired" from "that's not even a reset token"
 *       from "your account is gone."</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private OtpService otpService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    // Built in @BeforeEach, not as a field initializer: field initializers run
    // during instance construction, which happens BEFORE MockitoExtension
    // injects the @Mock fields (that injection is a postProcessTestInstance
    // callback that runs after construction). Building `service` as a field
    // initializer freezes it around still-null mocks — and since the field is
    // final, it stays wired to nulls for the lifetime of the test.
    private PasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetServiceImpl(userRepository, otpService, passwordEncoder, jwtService);
    }

    private User user(Long id, String username, String email) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        return u;
    }

    private ForgotPasswordRequest forgotPasswordRequest(String email) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return request;
    }

    private VerifyResetOtpRequest verifyResetOtpRequest(String email, String code) {
        VerifyResetOtpRequest request = new VerifyResetOtpRequest();
        request.setEmail(email);
        request.setCode(code);
        return request;
    }

    private ResetPasswordRequest resetPasswordRequest(String token, String newPassword, String confirmPassword) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setResetToken(token);
        request.setNewPassword(newPassword);
        request.setConfirmNewPassword(confirmPassword);
        return request;
    }

    // ---- forgotPassword -------------------------------------------------

    @Test
    void forgotPassword_sendsAPasswordResetOtp_whenTheEmailMatchesAnAccount() {
        User user = user(1L, "johndoe", "john@example.com");
        when(userRepository.findByUsernameOrEmail("john@example.com", "john@example.com")).thenReturn(Optional.of(user));

        service.forgotPassword(forgotPasswordRequest("john@example.com"));

        verify(otpService).generateAndSendOtp(user, OtpPurpose.PASSWORD_RESET);
    }

    @Test
    void forgotPassword_doesNothingAndDoesNotThrow_whenTheEmailMatchesNoAccount() {
        when(userRepository.findByUsernameOrEmail("ghost@example.com", "ghost@example.com")).thenReturn(Optional.empty());

        assertThatCode(() -> service.forgotPassword(forgotPasswordRequest("ghost@example.com")))
                .doesNotThrowAnyException();

        verifyNoInteractions(otpService);
    }

    // ---- verifyResetOtp -------------------------------------------------

    @Test
    void verifyResetOtp_throwsInvalidOtpException_whenTheEmailMatchesNoAccount() {
        when(userRepository.findByUsernameOrEmail("ghost@example.com", "ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyResetOtp(verifyResetOtpRequest("ghost@example.com", "123456")))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Invalid or expired code.");

        verifyNoInteractions(otpService, jwtService);
    }

    @Test
    void verifyResetOtp_delegatesCodeCheckingEntirelyToOtpService_andPropagatesItsExceptions() {
        User user = user(1L, "johndoe", "john@example.com");
        when(userRepository.findByUsernameOrEmail("john@example.com", "john@example.com")).thenReturn(Optional.of(user));
        OtpExpiredException expired = new OtpExpiredException("This code has expired. Please request a new one.");
        org.mockito.Mockito.doThrow(expired).when(otpService).verifyOtp(1L, OtpPurpose.PASSWORD_RESET, "123456");

        assertThatThrownBy(() -> service.verifyResetOtp(verifyResetOtpRequest("john@example.com", "123456")))
                .isSameAs(expired);

        verifyNoInteractions(jwtService);
    }

    @Test
    void verifyResetOtp_onASuccessfulVerify_mintsAndReturnsAPasswordResetToken() {
        User user = user(1L, "johndoe", "john@example.com");
        when(userRepository.findByUsernameOrEmail("john@example.com", "john@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generatePasswordResetToken(any(UserDetails.class))).thenReturn("reset-jwt");

        VerifyResetOtpResponse response = service.verifyResetOtp(verifyResetOtpRequest("john@example.com", "123456"));

        verify(otpService).verifyOtp(1L, OtpPurpose.PASSWORD_RESET, "123456");
        assertThat(response.getResetToken()).isEqualTo("reset-jwt");

        ArgumentCaptor<UserDetails> captor = ArgumentCaptor.forClass(UserDetails.class);
        verify(jwtService).generatePasswordResetToken(captor.capture());
        assertThat(((UserDetail) captor.getValue()).getUser()).isSameAs(user);
    }

    // ---- resetPassword -------------------------------------------------

    @Test
    void resetPassword_throwsPasswordMisMatchException_whenTheTwoPasswordsDiffer() {
        assertThatThrownBy(() -> service.resetPassword(resetPasswordRequest("token", "Passw0rd!", "Different1")))
                .isInstanceOf(PasswordMisMatchException.class)
                .hasMessage("Passwords do not match");

        verifyNoInteractions(jwtService, userRepository, passwordEncoder);
    }

    @Test
    void resetPassword_throwsInvalidResetTokenException_whenTheTokenIsNotAPasswordResetToken() {
        when(jwtService.isPasswordResetToken("token")).thenReturn(false);

        assertThatThrownBy(() -> service.resetPassword(resetPasswordRequest("token", "Passw0rd!", "Passw0rd!")))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessage("Invalid or expired reset link. Please request a new one.");

        verify(jwtService, never()).extractUsername(anyString());
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void resetPassword_throwsInvalidResetTokenException_whenIsPasswordResetTokenThrowsJwtException() {
        // Covers an expired or malformed token - jjwt's parser throws JwtException
        // on the very first claim read, before the app ever sees a purpose claim.
        when(jwtService.isPasswordResetToken("expired-token")).thenThrow(new JwtException("expired"));

        assertThatThrownBy(() -> service.resetPassword(resetPasswordRequest("expired-token", "Passw0rd!", "Passw0rd!")))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessage("Invalid or expired reset link. Please request a new one.");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void resetPassword_throwsInvalidResetTokenException_whenExtractUsernameThrowsJwtException() {
        when(jwtService.isPasswordResetToken("token")).thenReturn(true);
        when(jwtService.extractUsername("token")).thenThrow(new JwtException("corrupt"));

        assertThatThrownBy(() -> service.resetPassword(resetPasswordRequest("token", "Passw0rd!", "Passw0rd!")))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessage("Invalid or expired reset link. Please request a new one.");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void resetPassword_throwsInvalidResetTokenException_whenTheTokensUserNoLongerExists() {
        when(jwtService.isPasswordResetToken("token")).thenReturn(true);
        when(jwtService.extractUsername("token")).thenReturn("deleted-user");
        when(userRepository.findByUsername("deleted-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(resetPasswordRequest("token", "Passw0rd!", "Passw0rd!")))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessage("Invalid or expired reset link. Please request a new one.");

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void resetPassword_onAValidToken_encodesAndSavesTheNewPassword() {
        when(jwtService.isPasswordResetToken("token")).thenReturn(true);
        when(jwtService.extractUsername("token")).thenReturn("johndoe");
        User user = user(1L, "johndoe", "john@example.com");
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassw0rd!")).thenReturn("$encoded-new-password");

        service.resetPassword(resetPasswordRequest("token", "NewPassw0rd!", "NewPassw0rd!"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$encoded-new-password");
    }
}