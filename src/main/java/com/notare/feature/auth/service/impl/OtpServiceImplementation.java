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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
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
    public void verifyOtpByIdentifier(String notarianIdOrEmail, OtpPurpose purpose, String submittedCode) {
        // Same message whether the account is unknown or the code is wrong. An
        // unknown identifier must not answer differently from a bad code, or
        // this endpoint becomes a registered-address oracle for anyone with a
        // list of emails.
        User user = findByIdentifier(notarianIdOrEmail)
                .orElseThrow(() -> new InvalidOtpException("Invalid or expired code."));

        verifyOtp(user.getId(), purpose, submittedCode);
    }

    @Override
    @Transactional
    public void resendOtp(ResendOtpRequest request) {
        // @Transactional matters here, and its absence was a real (if small)
        // hole: this method calls generateAndSendOtp, which is annotated
        // @Transactional — but a call to another method on the SAME bean does
        // not go through the Spring proxy, so the annotation on the target was
        // silently ignored and the cooldown check and the OTP insert ran
        // without a shared transaction. Annotating the entry point fixes it,
        // because the controller's call DOES cross the proxy.
        findByIdentifier(request.getNotarianIdOrEmail()).ifPresent(user -> {
            try {
                generateAndSendOtp(user, OtpPurpose.EMAIL_VERIFICATION);
            } catch (OtpResendCooldownException ex) {
                // Swallowed on purpose, and this is the whole reason the method
                // is shaped this way.
                //
                // An unknown identifier returns 200 with a deliberately vague
                // message. If a known one could return 429, the pair becomes a
                // clean oracle: send two resends for any email address and the
                // second answer tells you whether it is registered. That
                // mattered little when the identifier was an unguessable
                // notarianId; it matters a great deal now that it can be an
                // email address anyone can type.
                //
                // The honest user loses nothing. A cooldown means a code was
                // sent seconds ago and is valid for ten minutes — it is already
                // in their inbox. The client enforces its own visible countdown,
                // which it needs for the UX regardless of what the server says.
                log.debug("Resend suppressed by cooldown for user {}", user.getId());
            }
        });
    }

    /**
     * Resolve an account from either identifier form.
     *
     * <p>The discriminator is '@', which a notarianId can never contain: the
     * generator's alphabet is consonants and vowels only. Cheaper and clearer
     * than one derived OR query, and it lets each form be normalised the way it
     * is stored — emails are lower-cased at registration, notarianIds are
     * generated in upper case — so a user typing their address with a capital
     * letter is still found.
     *
     * <p>Returns empty rather than throwing. Every caller has its own view of
     * what a miss means: verify reports the same message as a wrong code,
     * resend does nothing at all. Neither reveals whether the account exists.
     */
    private Optional<User> findByIdentifier(String notarianIdOrEmail) {
        if (notarianIdOrEmail == null || notarianIdOrEmail.isBlank()) {
            return Optional.empty();
        }

        String identifier = notarianIdOrEmail.strip();

        return identifier.contains("@")
                ? userRepository.findByEmail(identifier.toLowerCase())
                : userRepository.findByNotarianId(identifier.toUpperCase());
    }

    @Override
    @Transactional
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