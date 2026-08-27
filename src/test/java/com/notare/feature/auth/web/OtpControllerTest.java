package com.notare.feature.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.exception.InvalidOtpException;
import com.notare.common.exception.OtpAttemptsExceededException;
import com.notare.common.exception.OtpExpiredException;
import com.notare.common.exception.OtpResendCooldownException;
import com.notare.config.OtpProperties;
import com.notare.feature.auth.dto.request.VerifyOtpRequest;
import com.notare.feature.auth.enums.OtpPurpose;
import com.notare.feature.auth.service.OtpService;
import com.notare.feature.auth.validation.OtpCodeValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15c of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - see {@link AuthControllerTest}'s
 * disclaimer (Step 15a); same reasoning. Run via {@code mvn test} before
 * trusting it.
 *
 * <p>{@code OtpController} needs no {@code @ClientIp}/{@code @UserAgent}
 * resolvers or {@code WebMvcConfig}, but its {@link VerifyOtpRequest} field
 * {@code code} carries the app's one custom bean-validation constraint,
 * {@code @ValidOtpCode}. {@code @WebMvcTest} does not component-scan plain
 * {@code @Component} classes outside the controller-shaped set, so without
 * explicitly importing {@link OtpCodeValidator} (and the
 * {@link OtpProperties} it depends on to know the configured code length),
 * that constraint would be silently absent from validation and every
 * malformed-code test below would pass for the wrong reason - the request
 * would simply never be checked. Both are imported so the real validator,
 * bound to {@code app.otp.length=6} from {@code application.properties},
 * actually runs.
 *
 * <p>Both endpoints are on {@code WebSecurityConfig}'s permit-all list
 * ({@code POST /api/v1/otp/**}), so {@code addFilters = false} matches how
 * the endpoint is actually reachable - unauthenticated.
 *
 * <p>Covers: {@code verify-otp} - happy path forwards identifier/purpose/code
 * to the service and returns the fixed success message; a blank identifier
 * and a malformed code (wrong length, non-digit) are rejected by validation
 * before the service runs; and {@link InvalidOtpException},
 * {@link OtpExpiredException}, {@link OtpAttemptsExceededException} from the
 * service each surface with the right status. {@code resend-otp} - happy
 * path forwards the request and always returns the same
 * non-committal message regardless of what the service does internally
 * (per the controller's own javadoc on why that wording must never change
 * based on account existence), plus the blank-identifier 400.
 */
@WebMvcTest(controllers = OtpController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({OtpProperties.class, OtpCodeValidator.class})
class OtpControllerTest {

    private static final String VERIFY_URL = "/api/v1/otp/verify-otp";
    private static final String RESEND_URL = "/api/v1/otp/resend-otp";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OtpService otpService;

    // ----------------------------------------------------------------
    // verify-otp
    // ----------------------------------------------------------------

    @Test
    void verifyOtp_validRequest_returns200AndForwardsToTheServiceWithEmailVerificationPurpose() throws Exception {
        doNothing().when(otpService).verifyOtpByIdentifier(anyString(), any(), anyString());

        mockMvc.perform(post(VERIFY_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(verifyBody("jdoe@example.com", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account verified successfully."));

        verify(otpService).verifyOtpByIdentifier(
                eq("jdoe@example.com"), eq(OtpPurpose.EMAIL_VERIFICATION), eq("123456"));
    }

    @Test
    void verifyOtp_blankIdentifier_returns400BeforeReachingTheService() throws Exception {
        mockMvc.perform(post(VERIFY_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(verifyBody("", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.notarianIdOrEmail")
                        .value("Your Notarian ID or email address is required."));

        verifyNoInteractions(otpService);
    }

    @Test
    void verifyOtp_codeWrongLength_returns400WithTheConfiguredLengthInTheMessage() throws Exception {
        // Exercises the REAL OtpCodeValidator bound to app.otp.length=6 from
        // application.properties - the message text ("6 digits") is produced
        // by the validator reading OtpProperties, not hardcoded here.
        mockMvc.perform(post(VERIFY_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(verifyBody("jdoe@example.com", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("Code must be 6 digits."));

        verifyNoInteractions(otpService);
    }

    @Test
    void verifyOtp_codeContainsNonDigits_returns400() throws Exception {
        mockMvc.perform(post(VERIFY_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(verifyBody("jdoe@example.com", "12a45b"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("Code must be 6 digits."));

        verifyNoInteractions(otpService);
    }

    @Test
    void verifyOtp_serviceRejectsAnIncorrectCode_returns400ViaGlobalExceptionHandler() throws Exception {
        doThrow(new InvalidOtpException("The code you entered is incorrect."))
                .when(otpService).verifyOtpByIdentifier(anyString(), any(), anyString());

        mockMvc.perform(post(VERIFY_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(verifyBody("jdoe@example.com", "654321"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The code you entered is incorrect."));
    }

    @Test
    void verifyOtp_serviceReportsAnExpiredCode_returns400ViaGlobalExceptionHandler() throws Exception {
        doThrow(new OtpExpiredException("This code has expired."))
                .when(otpService).verifyOtpByIdentifier(anyString(), any(), anyString());

        mockMvc.perform(post(VERIFY_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(verifyBody("jdoe@example.com", "654321"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This code has expired."));
    }

    @Test
    void verifyOtp_tooManyIncorrectAttempts_returns429ViaGlobalExceptionHandler() throws Exception {
        doThrow(new OtpAttemptsExceededException("Too many incorrect attempts."))
                .when(otpService).verifyOtpByIdentifier(anyString(), any(), anyString());

        mockMvc.perform(post(VERIFY_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(verifyBody("jdoe@example.com", "654321"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Too many incorrect attempts."));
    }

    // ----------------------------------------------------------------
    // resend-otp
    // ----------------------------------------------------------------

    @Test
    void resendOtp_validRequest_returns200WithTheFixedNonCommittalMessage() throws Exception {
        doNothing().when(otpService).resendOtp(any());

        mockMvc.perform(post(RESEND_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(resendBody("jdoe@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("If an account with that ID exists, a new code has been sent."));

        verify(otpService, times(1)).resendOtp(any());
    }

    @Test
    void resendOtp_evenWhenTheServiceSwallowsACooldown_stillReturnsTheSameFixedMessage() throws Exception {
        // The controller's own javadoc: the service swallows the resend
        // cooldown internally rather than throwing, specifically so the
        // response never differs between "cooldown active" and "unknown
        // account" - both must look identical to the caller. A mock that
        // simply completes normally is the correct stand-in for that
        // swallowed-internally behavior; what matters here is the response
        // shape never changes.
        doNothing().when(otpService).resendOtp(any());

        mockMvc.perform(post(RESEND_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(resendBody("someone-else@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("If an account with that ID exists, a new code has been sent."));
    }

    @Test
    void resendOtp_blankIdentifier_returns400BeforeReachingTheService() throws Exception {
        mockMvc.perform(post(RESEND_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(resendBody(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.notarianIdOrEmail")
                        .value("Your Notarian ID or email address is required."));

        verify(otpService, never()).resendOtp(any());
    }

    @Test
    void resendOtp_identifierTooLong_returns400() throws Exception {
        mockMvc.perform(post(RESEND_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(resendBody("a".repeat(101)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.notarianIdOrEmail")
                        .value("Identifier must not exceed 100 characters."));

        verify(otpService, never()).resendOtp(any());
    }

    /**
     * The one exception explicitly NOT expected from resendOtp - if the
     * service ever did throw this instead of swallowing it, the whole point
     * of the endpoint's non-committal design would be defeated (200 for one
     * caller, 429 for another reveals which identifiers are real). Not
     * something the controller guards against itself, so this documents the
     * expectation rather than asserting a controller-level guard: the
     * OtpService contract, not this handler, is what must never throw this
     * from resendOtp in practice.
     */
    @Test
    void resendOtp_ifTheServiceEverDidThrowCooldown_itWouldStillMapToA429() throws Exception {
        doThrow(new OtpResendCooldownException("Please wait before requesting another code."))
                .when(otpService).resendOtp(any());

        mockMvc.perform(post(RESEND_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(resendBody("jdoe@example.com"))))
                .andExpect(status().isTooManyRequests());
    }

    private Map<String, String> verifyBody(String identifier, String code) {
        Map<String, String> body = new HashMap<>();
        body.put("notarianIdOrEmail", identifier);
        body.put("code", code);
        return body;
    }

    private Map<String, String> resendBody(String identifier) {
        Map<String, String> body = new HashMap<>();
        body.put("notarianIdOrEmail", identifier);
        return body;
    }
}