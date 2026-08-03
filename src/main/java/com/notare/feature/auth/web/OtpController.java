package com.notare.feature.auth.web;

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
    public ResponseEntity<String> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        otpService.verifyOtpByNotarianId(request.getNotarianId(), OtpPurpose.EMAIL_VERIFICATION, request.getCode());
        return ResponseEntity.ok("Account verified successfully.");
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<String> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        otpService.resendOtp(request);
        return ResponseEntity.ok("If an account with that ID exists, a new code has been sent.");
    }
}