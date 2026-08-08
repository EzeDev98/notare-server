package com.notare.feature.passwordreset.web;

import com.notare.common.dto.MessageResponse;
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

    /**
     * Answers identically whether or not the email is registered. That is the
     * point, and the wording is unchanged from the plain-text version.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        resetPassword.forgotPassword(request);
        return ResponseEntity.ok(new MessageResponse("A reset code has been sent you."));
    }

    /**
     * Already returned JSON — VerifyResetOtpResponse carries the reset token.
     * Left alone.
     */
    @PostMapping("/verify-reset-otp")
    public ResponseEntity<VerifyResetOtpResponse> verifyResetOtp(@Valid @RequestBody VerifyResetOtpRequest request) {
        VerifyResetOtpResponse response = resetPassword.verifyResetOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        resetPassword.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("Password reset successfully."));
    }
}