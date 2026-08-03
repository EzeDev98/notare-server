package com.notare.feature.passwordreset.web;

import com.notare.feature.passwordreset.dto.request.ForgotPasswordRequest;
import com.notare.feature.passwordreset.dto.request.ResetPasswordRequest;
import com.notare.feature.passwordreset.dto.request.VerifyResetOtpRequest;
import com.notare.feature.passwordreset.dto.response.VerifyResetOtpResponse;
import com.notare.feature.passwordreset.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/password")
public class PasswordResetController {

    private final PasswordResetService resetPassword;

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        resetPassword.forgotPassword(request);
        return ResponseEntity.ok("If an account exists for this email, a reset code has been sent.");
    }

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<VerifyResetOtpResponse> verifyResetOtp(@Valid @RequestBody VerifyResetOtpRequest request) {
        VerifyResetOtpResponse response = resetPassword.verifyResetOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        resetPassword.resetPassword(request);
        return ResponseEntity.ok("Password reset successfully.");
    }
}
