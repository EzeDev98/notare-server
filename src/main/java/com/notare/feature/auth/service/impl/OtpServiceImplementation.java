package com.notare.feature.auth.service.impl;

import com.notare.common.exception.InvalidOtpException;
import com.notare.common.exception.OtpAttemptsExceededException;
import com.notare.common.exception.OtpExpiredException;
import com.notare.common.exception.OtpResendCooldownException;
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
import java.util.Optional;

import static com.notare.common.constant.GeneralConstant.*;

@RequiredArgsConstructor
@Service
public class OtpServiceImplementation implements OtpService {

    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final UserRepository userRepository;

    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public void generateAndSendOtp(User user, OtpPurpose purpose) {

        enforceCoolDown(user.getId(), purpose);

        String code = generateNumericCode();

        Otp otp = new Otp();
        otp.setUserId(user.getId());
        otp.setOtpPurpose(purpose);
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otp.setConsumed(false);
        otp.setAttempts(0);

        emailService.sendEmail(user.getEmail(), user.getUsername(), code);
    }

    @Override
    @Transactional
    public void verifyOtp(Long userId, OtpPurpose purpose, String submittedCode) {
        Otp otp = otpRepository.findTopByUserIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(userId, purpose)
                .orElseThrow(() -> new InvalidOtpException("No active verification code found. Please request a new one."));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new OtpExpiredException("This code has expired. Please request a new one.");
        }

        if (otp.getAttempts() >= MAX_OTP_ATTEMPTS) {
            throw new OtpAttemptsExceededException("Too many incorrect attempts. Please request a new code.");
        }

        if (!passwordEncoder.matches(submittedCode, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            throw new InvalidOtpException("Incorrect code. Please try again.");
        }

        otp.setConsumed(true);
        otpRepository.save(otp);

        if (purpose == OtpPurpose.EMAIL_VERIFICATION) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new InvalidOtpException("User not found"));

            user.setEmailVerified(true);
            user.setUserStatus(UserStatus.ACTIVE);
            userRepository.save(user);
        }

    }

    private void enforceCoolDown(Long userId, OtpPurpose purpose) {
        Optional<Otp> latest = otpRepository.findTopByUserIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(userId, purpose);

        latest.ifPresent(otp -> {
            Duration sinceCreated = Duration.between(otp.getCreatedAt(), LocalDateTime.now());
            if (sinceCreated.getSeconds() < RESEND_COOLDOWN_SECONDS) {
                throw new OtpResendCooldownException("Please wait before requesting another code.");
            }
        });
    }


    private String generateNumericCode() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}
