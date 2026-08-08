package com.notare.feature.auth.web;

import com.notare.common.dto.MessageResponse;
import com.notare.feature.auth.dto.request.ResendOtpRequest;
import com.notare.feature.auth.dto.request.VerifyOtpRequest;
import com.notare.feature.auth.enums.OtpPurpose;
import com.notare.feature.auth.service.OtpService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/otp")
public class OtpController {

    private final OtpService otpService;

    /**
     * Verify an account with the code emailed at registration.
     *
     * <p>The account is named by notarianId OR email. Before this, only the
     * notarianId was accepted — and it is returned exactly once, in the
     * registration response, and appears in nothing the user keeps. Closing the
     * tab before verifying left the account permanently unreachable: unable to
     * verify, unable to resend, unable to log in (unverified is a 403), and
     * unable to register again because the email was taken.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<MessageResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        otpService.verifyOtpByIdentifier(
                request.getNotarianIdOrEmail(), OtpPurpose.EMAIL_VERIFICATION, request.getCode());
        return ResponseEntity.ok(new MessageResponse("Account verified successfully."));
    }

    /**
     * Send a fresh verification code.
     *
     * <p>Deliberately non-committal wording: it does not confirm whether the
     * account exists. That phrasing is a security decision rather than a
     * formatting one, and it now has to hold against an EMAIL address rather
     * than an unguessable notarianId — which is why the service also swallows
     * the resend cooldown rather than returning 429. Two resends for the same
     * address would otherwise answer 200 then 429 for a real account and 200
     * twice for a fake one, which is a registered-address oracle in two
     * requests.
     *
     * <p>The client must therefore run its own visible cooldown timer after a
     * successful call. It needs one for the UX anyway; it can no longer learn
     * about the server's from a status code.
     */
    @PostMapping("/resend-otp")
    public ResponseEntity<MessageResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        otpService.resendOtp(request);
        return ResponseEntity.ok(new MessageResponse("If an account with that ID exists, a new code has been sent."));
    }
}