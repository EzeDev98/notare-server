package com.notare.feature.otp.controller;

import com.notare.feature.auth.enums.OtpPurpose;
import com.notare.feature.auth.service.OtpService;
import com.notare.feature.user.model.User;
import com.notare.feature.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        otpService.verifyOtp(request.getUserId(), OtpPurpose.EMAIL_VERIFICATION, request.getCode());
        return ResponseEntity.ok("Account verified successfully.");
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<String> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        otpService.generateAndSendOtp(user, OtpPurpose.EMAIL_VERIFICATION);
        return ResponseEntity.ok("A new code has been sent.");
    }
}