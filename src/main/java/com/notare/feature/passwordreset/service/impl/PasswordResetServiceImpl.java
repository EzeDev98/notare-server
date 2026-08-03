package com.notare.feature.passwordreset.service.impl;

import com.notare.common.exception.InvalidOtpException;
import com.notare.common.exception.InvalidResetTokenException;
import com.notare.common.exception.PasswordMisMatchException;
import com.notare.common.security.JwtService;

import com.notare.feature.auth.enums.OtpPurpose;
import com.notare.feature.auth.service.OtpService;
import com.notare.feature.passwordreset.dto.request.ForgotPasswordRequest;
import com.notare.feature.passwordreset.dto.request.ResetPasswordRequest;
import com.notare.feature.passwordreset.dto.request.VerifyResetOtpRequest;
import com.notare.feature.passwordreset.dto.response.VerifyResetOtpResponse;
import com.notare.feature.passwordreset.service.PasswordResetService;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByUsernameOrEmail(request.getEmail(), request.getEmail())
                .ifPresentOrElse(
                        user -> otpService.generateAndSendOtp(user, OtpPurpose.PASSWORD_RESET),
                        () -> log.info("Password reset requested for unregistered email")
                );
    }

    @Override
    public VerifyResetOtpResponse verifyResetOtp(VerifyResetOtpRequest request) {

        User user = userRepository.findByUsernameOrEmail(request.getEmail(), request.getEmail())
                .orElseThrow(() -> new InvalidOtpException("Invalid or expired code."));

        otpService.verifyOtp(user.getId(), OtpPurpose.PASSWORD_RESET, request.getCode());

        String resetToken = jwtService.generatePasswordResetToken(new UserDetail(user));
        return new VerifyResetOtpResponse(resetToken);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new PasswordMisMatchException("Passwords do not match");
        }

        String username;
        try {
            if (!jwtService.isPasswordResetToken(request.getResetToken())) {
                throw new InvalidResetTokenException("Invalid or expired reset link. Please request a new one.");
            }
            username = jwtService.extractUsername(request.getResetToken());
        } catch (JwtException ex) {
            throw new InvalidResetTokenException("Invalid or expired reset link. Please request a new one.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidResetTokenException("Invalid or expired reset link. Please request a new one."));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}