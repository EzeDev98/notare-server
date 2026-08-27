package com.notare.feature.auth.service.impl;

import com.notare.common.exception.InvalidOtpException;
import com.notare.common.exception.OtpAttemptsExceededException;
import com.notare.common.exception.OtpExpiredException;
import com.notare.common.exception.OtpResendCooldownException;
import com.notare.common.exception.UserNotFoundException;
import com.notare.config.OtpProperties;
import com.notare.feature.auth.dto.request.ResendOtpRequest;
import com.notare.feature.auth.enums.OtpPurpose;
import com.notare.feature.auth.model.Otp;
import com.notare.feature.auth.respository.OtpRepository;
import com.notare.feature.auth.service.EmailService;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Step 6a of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - Mockito and Spring Security
 * ({@code PasswordEncoder}) are unavailable as compile-time dependencies here
 * (see {@link com.notare.feature.user.service.impl.UserServiceImplementationTest}
 * for the full explanation). Reasoned carefully from source; run via
 * {@code mvn test} before trusting it.
 *
 * <p>Covers every branch of {@link OtpServiceImplementation}: the resend
 * cooldown, code generation/hashing/expiry, the ordered checks in {@code
 * verifyOtp} (expired before attempts-exceeded before wrong-code, each with
 * its own exception type), the purpose-keyed post-verification action (only
 * {@code EMAIL_VERIFICATION} activates the account - {@code PASSWORD_RESET}
 * does nothing extra), and the identifier-oracle protections in {@code
 * verifyOtpByIdentifier} and {@code resendOtp} (an unknown identifier and a
 * wrong code must be indistinguishable to the caller, and a cooldown must
 * never surface for an identifier that isn't even registered).
 */
@ExtendWith(MockitoExtension.class)
class OtpServiceImplementationTest {

    @Mock private OtpRepository otpRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private UserRepository userRepository;
    @Mock private OtpProperties otpProperties;

    private OtpServiceImplementation service;

    @BeforeEach
    void setUp() {
        lenient().when(otpProperties.getLength()).thenReturn(6);
        lenient().when(otpProperties.getExpiryMinutes()).thenReturn(10);
        lenient().when(otpProperties.getMaxAttempts()).thenReturn(5);
        lenient().when(otpProperties.getResendCooldownSeconds()).thenReturn(60);
        service = new OtpServiceImplementation(otpRepository, passwordEncoder, emailService, userRepository, otpProperties);
    }

    private User user(Long id, String username, String email) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        u.setUserStatus(UserStatus.PENDING_VERIFICATION);
        u.setEmailVerified(false);
        return u;
    }

    private Otp otp(Long userId, OtpPurpose purpose, String codeHash, int attempts, boolean consumed,
                    LocalDateTime createdAt, LocalDateTime expiresAt) {
        Otp o = new Otp();
        o.setUserId(userId);
        o.setOtpPurpose(purpose);
        o.setCodeHash(codeHash);
        o.setAttempts(attempts);
        o.setConsumed(consumed);
        o.setCreatedAt(createdAt);
        o.setExpiresAt(expiresAt);
        return o;
    }

    // ---- generateAndSendOtp -------------------------------------------------

    @Test
    void generateAndSendOtp_throwsOtpResendCooldownException_whenARecentUnconsumedOtpIsStillWithinCooldown() {
        User user = user(1L, "johndoe", "john@example.com");
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(otp(1L, OtpPurpose.EMAIL_VERIFICATION, "hash", 0, false,
                        LocalDateTime.now().minusSeconds(10), LocalDateTime.now().plusMinutes(10))));

        assertThatThrownBy(() -> service.generateAndSendOtp(user, OtpPurpose.EMAIL_VERIFICATION))
                .isInstanceOf(OtpResendCooldownException.class)
                .hasMessage("Please wait before requesting another code.");

        verifyNoInteractions(emailService);
        verify(otpRepository, never()).save(any(Otp.class));
    }

    @Test
    void generateAndSendOtp_allowsANewOtp_whenTheExistingOneIsOlderThanTheCooldown() {
        User user = user(1L, "johndoe", "john@example.com");
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(otp(1L, OtpPurpose.EMAIL_VERIFICATION, "hash", 0, false,
                        LocalDateTime.now().minusSeconds(120), LocalDateTime.now().plusMinutes(10))));
        when(passwordEncoder.encode(anyString())).thenReturn("$encoded");

        assertThatCode(() -> service.generateAndSendOtp(user, OtpPurpose.EMAIL_VERIFICATION)).doesNotThrowAnyException();

        verify(otpRepository).save(any(Otp.class));
    }

    @Test
    void generateAndSendOtp_allowsANewOtp_whenNoActiveOtpExistsForThatPurpose() {
        User user = user(1L, "johndoe", "john@example.com");
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$encoded");

        assertThatCode(() -> service.generateAndSendOtp(user, OtpPurpose.EMAIL_VERIFICATION)).doesNotThrowAnyException();

        verify(otpRepository).save(any(Otp.class));
    }

    @Test
    void generateAndSendOtp_savesAHashedCodeWithTheConfiguredExpiry_unconsumedWithZeroAttempts() {
        User user = user(1L, "johndoe", "john@example.com");
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.PASSWORD_RESET))
                .thenReturn(Optional.empty());
        when(otpProperties.getExpiryMinutes()).thenReturn(10);
        when(passwordEncoder.encode(anyString())).thenReturn("$hashed-code");

        LocalDateTime before = LocalDateTime.now();
        service.generateAndSendOtp(user, OtpPurpose.PASSWORD_RESET);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<Otp> captor = ArgumentCaptor.forClass(Otp.class);
        verify(otpRepository).save(captor.capture());
        Otp saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getOtpPurpose()).isEqualTo(OtpPurpose.PASSWORD_RESET);
        assertThat(saved.getCodeHash()).isEqualTo("$hashed-code");
        assertThat(saved.isConsumed()).isFalse();
        assertThat(saved.getAttempts()).isEqualTo(0);
        assertThat(saved.getExpiresAt()).isBetween(before.plusMinutes(10).minusSeconds(2), after.plusMinutes(10).plusSeconds(2));
    }

    @Test
    void generateAndSendOtp_sendsThePlainNumericCodeByEmail_notTheHash() {
        User user = user(1L, "johndoe", "john@example.com");
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$hashed-and-not-the-real-code");

        service.generateAndSendOtp(user, OtpPurpose.EMAIL_VERIFICATION);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq("john@example.com"), eq("johndoe"), codeCaptor.capture());
        assertThat(codeCaptor.getValue()).isNotEqualTo("$hashed-and-not-the-real-code");
        assertThat(codeCaptor.getValue()).matches("\\d+");
    }

    @Test
    void generateAndSendOtp_generatesACodeWithExactlyTheConfiguredDigitLength() {
        when(otpProperties.getLength()).thenReturn(6);
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$hash");

        for (int i = 0; i < 25; i++) {
            User user = user(1L, "johndoe", "john@example.com");
            service.generateAndSendOtp(user, OtpPurpose.EMAIL_VERIFICATION);
        }

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(25)).sendEmail(anyString(), anyString(), codeCaptor.capture());
        for (String code : codeCaptor.getAllValues()) {
            assertThat(code).hasSize(6);
            // A leading zero would make it a 5-digit number formatted as 6 - the
            // lowerBound/range math in generateNumericCode() must rule this out.
            assertThat(code.charAt(0)).isNotEqualTo('0');
        }
    }

    // ---- verifyOtp -------------------------------------------------

    @Test
    void verifyOtp_throwsInvalidOtpException_whenNoActiveOtpExists() {
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOtp(1L, OtpPurpose.EMAIL_VERIFICATION, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("No active verification code found. Please request a new one.");
    }

    @Test
    void verifyOtp_throwsOtpExpiredException_whenTheOtpHasExpired() {
        Otp otp = otp(1L, OtpPurpose.EMAIL_VERIFICATION, "hash", 0, false,
                LocalDateTime.now().minusMinutes(20), LocalDateTime.now().minusMinutes(10));
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.verifyOtp(1L, OtpPurpose.EMAIL_VERIFICATION, "123456"))
                .isInstanceOf(OtpExpiredException.class)
                .hasMessage("This code has expired. Please request a new one.");

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void verifyOtp_throwsOtpAttemptsExceededException_beforeEverCheckingTheSubmittedCode() {
        Otp otp = otp(1L, OtpPurpose.EMAIL_VERIFICATION, "hash", 5, false,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(9));
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(otp));
        when(otpProperties.getMaxAttempts()).thenReturn(5);

        assertThatThrownBy(() -> service.verifyOtp(1L, OtpPurpose.EMAIL_VERIFICATION, "123456"))
                .isInstanceOf(OtpAttemptsExceededException.class)
                .hasMessage("Too many incorrect attempts. Please request a new code.");

        // The exceeded-attempts check happens before the code is ever compared -
        // even the RIGHT code must not slip through once the ceiling is hit.
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void verifyOtp_onAWrongCode_incrementsAttemptsAndSaves_thenThrowsInvalidOtpException() {
        Otp otp = otp(1L, OtpPurpose.EMAIL_VERIFICATION, "hash", 2, false,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(9));
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("000000", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyOtp(1L, OtpPurpose.EMAIL_VERIFICATION, "000000"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Incorrect code. Please try again.");

        ArgumentCaptor<Otp> captor = ArgumentCaptor.forClass(Otp.class);
        verify(otpRepository).save(captor.capture());
        assertThat(captor.getValue().getAttempts()).isEqualTo(3);
        assertThat(captor.getValue().isConsumed()).isFalse();
    }

    @Test
    void verifyOtp_onACorrectCodeForEmailVerification_marksConsumedAndActivatesTheUser() {
        Otp otp = otp(1L, OtpPurpose.EMAIL_VERIFICATION, "hash", 0, false,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(9));
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
        User user = user(1L, "johndoe", "john@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.verifyOtp(1L, OtpPurpose.EMAIL_VERIFICATION, "123456");

        ArgumentCaptor<Otp> otpCaptor = ArgumentCaptor.forClass(Otp.class);
        verify(otpRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().isConsumed()).isTrue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isEmailVerified()).isTrue();
        assertThat(userCaptor.getValue().getUserStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void verifyOtp_activation_throwsInvalidOtpException_whenTheUserNoLongerExists() {
        Otp otp = otp(1L, OtpPurpose.EMAIL_VERIFICATION, "hash", 0, false,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(9));
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOtp(1L, OtpPurpose.EMAIL_VERIFICATION, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("User not found");

        // The otp itself was already saved as consumed before the activation
        // lookup ran - a missing user does not roll that back on its own within
        // this method (the surrounding @Transactional boundary is what would).
        verify(otpRepository).save(any(Otp.class));
    }

    @Test
    void verifyOtp_onACorrectCodeForPasswordReset_marksConsumed_andNeverTouchesUserRepository() {
        Otp otp = otp(1L, OtpPurpose.PASSWORD_RESET, "hash", 0, false,
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(9));
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

        service.verifyOtp(1L, OtpPurpose.PASSWORD_RESET, "123456");

        verify(otpRepository).save(any(Otp.class));
        verifyNoInteractions(userRepository);
    }

    // ---- verifyOtpByIdentifier -------------------------------------------------

    @Test
    void verifyOtpByIdentifier_throwsInvalidOtpException_whenTheIdentifierMatchesNoAccount() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOtpByIdentifier("ghost@example.com", OtpPurpose.EMAIL_VERIFICATION, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Invalid or expired code.");

        verifyNoInteractions(otpRepository, passwordEncoder);
    }

    @Test
    void verifyOtpByIdentifier_routesAnAtSignIdentifier_toFindByEmail_lowercased() {
        User user = user(1L, "johndoe", "john@example.com");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOtpByIdentifier(" JOHN@EXAMPLE.COM ", OtpPurpose.EMAIL_VERIFICATION, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("No active verification code found. Please request a new one.");

        verify(userRepository).findByEmail("john@example.com");
        verify(userRepository, never()).findByNotarianId(anyString());
    }

    @Test
    void verifyOtpByIdentifier_routesANonAtSignIdentifier_toFindByNotarianId_uppercased() {
        User user = user(1L, "johndoe", "john@example.com");
        when(userRepository.findByNotarianId("BADAZOP")).thenReturn(Optional.of(user));
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOtpByIdentifier(" badazop ", OtpPurpose.EMAIL_VERIFICATION, "123456"))
                .isInstanceOf(InvalidOtpException.class);

        verify(userRepository).findByNotarianId("BADAZOP");
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void verifyOtpByIdentifier_throwsInvalidOtpException_forANullOrBlankIdentifier_withoutQueryingTheRepository() {
        assertThatThrownBy(() -> service.verifyOtpByIdentifier(null, OtpPurpose.EMAIL_VERIFICATION, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Invalid or expired code.");
        assertThatThrownBy(() -> service.verifyOtpByIdentifier("   ", OtpPurpose.EMAIL_VERIFICATION, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Invalid or expired code.");

        verifyNoInteractions(userRepository);
    }

    // ---- resendOtp -------------------------------------------------

    @Test
    void resendOtp_doesNothing_whenTheIdentifierMatchesNoAccount() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        ResendOtpRequest request = new ResendOtpRequest();
        request.setNotarianIdOrEmail("ghost@example.com");

        assertThatCode(() -> service.resendOtp(request)).doesNotThrowAnyException();

        verifyNoInteractions(emailService, otpRepository);
    }

    @Test
    void resendOtp_sendsANewCode_whenTheAccountExistsAndIsNotInCooldown() {
        User user = user(1L, "johndoe", "john@example.com");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$hash");
        ResendOtpRequest request = new ResendOtpRequest();
        request.setNotarianIdOrEmail("john@example.com");

        service.resendOtp(request);

        verify(emailService).sendEmail(eq("john@example.com"), eq("johndoe"), anyString());
    }

    @Test
    void resendOtp_silentlySwallowsTheCooldownException_forAKnownAccount() {
        User user = user(1L, "johndoe", "john@example.com");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(otp(1L, OtpPurpose.EMAIL_VERIFICATION, "hash", 0, false,
                        LocalDateTime.now().minusSeconds(5), LocalDateTime.now().plusMinutes(10))));
        ResendOtpRequest request = new ResendOtpRequest();
        request.setNotarianIdOrEmail("john@example.com");

        // Must NOT throw - a 429 here combined with the silent 200 for an
        // unknown identifier would be a registered-address oracle (see the
        // production class's own doc comment on this method).
        assertThatCode(() -> service.resendOtp(request)).doesNotThrowAnyException();

        verifyNoInteractions(emailService);
    }

    // ---- sendOtp -------------------------------------------------

    @Test
    void sendOtp_throwsUserNotFoundException_whenTheIdHasNoRow() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendOtp(404L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void sendOtp_generatesAndSendsAnEmailVerificationCode_forAnExistingUser() {
        User user = user(1L, "johndoe", "john@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(otpRepository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(1L, OtpPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$hash");

        service.sendOtp(1L);

        verify(emailService).sendEmail(eq("john@example.com"), eq("johndoe"), anyString());
        ArgumentCaptor<Otp> captor = ArgumentCaptor.forClass(Otp.class);
        verify(otpRepository).save(captor.capture());
        assertThat(captor.getValue().getOtpPurpose()).isEqualTo(OtpPurpose.EMAIL_VERIFICATION);
    }
}