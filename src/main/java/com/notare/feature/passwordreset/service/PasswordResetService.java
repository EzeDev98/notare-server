package com.notare.feature.passwordreset.service;


import com.notare.feature.passwordreset.dto.request.ForgotPasswordRequest;
import com.notare.feature.passwordreset.dto.request.ResetPasswordRequest;
import com.notare.feature.passwordreset.dto.request.VerifyResetOtpRequest;
import com.notare.feature.passwordreset.dto.response.VerifyResetOtpResponse;

public interface PasswordResetService {
    void forgotPassword(ForgotPasswordRequest request);
    VerifyResetOtpResponse verifyResetOtp(VerifyResetOtpRequest request);
    void resetPassword(ResetPasswordRequest request);
}