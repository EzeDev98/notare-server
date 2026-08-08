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

    @PostMapping("/verify-otp")
    public ResponseEntity<MessageResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        otpService.verifyOtpByNotarianId(request.getNotarianId(), OtpPurpose.EMAIL_VERIFICATION, request.getCode());
        return ResponseEntity.ok(new MessageResponse("Account verified successfully."));
    }

    /**
     * Deliberately non-committal wording: it does not confirm whether the
     * Notarian ID exists. Preserved verbatim from the plain-text version — the
     * response TYPE changed, the response CONTENT did not, because that phrasing
     * is a security decision rather than a formatting one.
     */
    @PostMapping("/resend-otp")
    public ResponseEntity<MessageResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        otpService.resendOtp(request);
        return ResponseEntity.ok(new MessageResponse("A new code has been sent to you."));
    }
}