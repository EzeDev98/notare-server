package com.notare.feature.auth.service.impl;

import com.notare.common.exception.*;
import com.notare.config.OtpProperties;
import com.notare.feature.auth.dto.request.ResendOtpRequest;
import com.notare.feature.auth.enums.OtpPurpose;
import com.notare.feature.auth.model.Otp;
import com.notare.feature.auth.respository.OtpRepository;
import com.notare.feature.auth.service.EmailService;
import com.notare.feature.auth.service.OtpService;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Service
public class OtpServiceImplementation implements OtpService {

    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final OtpProperties otpProperties;

    private final SecureRandom random = new SecureRandom();

    private final Map<OtpPurpose, Consumer<Long>> postVerificationActions = Map.of(
            OtpPurpose.EMAIL_VERIFICATION, this::activateUser
    );

    @Override
    @Transactional
    public void generateAndSendOtp(User user, OtpPurpose purpose) {

        enforceCoolDown(user.getId(), purpose);

        String code = generateNumericCode();

        Otp otp = new Otp();
        otp.setUserId(user.getId());
        otp.setOtpPurpose(purpose);
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(otpProperties.getExpiryMinutes()));
        otp.setConsumed(false);
        otp.setAttempts(0);

        otpRepository.save(otp);

        emailService.sendEmail(user.getEmail(), user.getUsername(), code);
    }

    @Override
    @Transactional
    public void verifyOtp(Long userId, OtpPurpose purpose, String submittedCode) {
        Otp otp = otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(userId, purpose)
                .orElseThrow(() -> new InvalidOtpException("No active verification code found. Please request a new one."));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new OtpExpiredException("This code has expired. Please request a new one.");
        }

        if (otp.getAttempts() >= otpProperties.getMaxAttempts()) {
            throw new OtpAttemptsExceededException("Too many incorrect attempts. Please request a new code.");
        }

        if (!passwordEncoder.matches(submittedCode, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            throw new InvalidOtpException("Incorrect code. Please try again.");
        }

        otp.setConsumed(true);
        otpRepository.save(otp);

        Optional.ofNullable(postVerificationActions.get(purpose))
                .ifPresent(action -> action.accept(userId));
    }

    private void activateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidOtpException("User not found"));

        user.setEmailVerified(true);
        user.setUserStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void verifyOtpByNotarianId(String notarianId, OtpPurpose purpose, String submittedCode) {
        User user = userRepository.findByNotarianId(notarianId)
                .orElseThrow(() -> new InvalidOtpException("Invalid or expired code."));
        verifyOtp(user.getId(), purpose, submittedCode);
    }

    @Override
    public void resendOtp(ResendOtpRequest request) {
        userRepository.findByNotarianId(request.getNotarianId())
                .ifPresentOrElse(
                        user -> generateAndSendOtp(user, OtpPurpose.EMAIL_VERIFICATION),
                        () -> {});
    }

    @Override
    public void sendOtp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        generateAndSendOtp(user, OtpPurpose.EMAIL_VERIFICATION);
    }

    private void enforceCoolDown(Long userId, OtpPurpose purpose) {
        Optional<Otp> latest = otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(userId, purpose);

        latest.ifPresent(otp -> {
            Duration sinceCreated = Duration.between(otp.getCreatedAt(), LocalDateTime.now());
            if (sinceCreated.getSeconds() < otpProperties.getResendCooldownSeconds()) {
                throw new OtpResendCooldownException("Please wait before requesting another code.");
            }
        });
    }


    private String generateNumericCode() {
        int length = otpProperties.getLength();
        int lowerBound = (int) Math.pow(10, length - 1);
        int range = (int) (9 * Math.pow(10, length - 1));
        int code = lowerBound + random.nextInt(range);
        return String.valueOf(code);
    }
}