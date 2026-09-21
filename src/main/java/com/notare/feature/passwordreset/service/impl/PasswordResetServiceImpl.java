package com.notare.feature.passwordreset.service.impl;

import com.notare.client.dto.request.CompletePasswordResetRequest;
import com.notare.client.dto.response.InternalAuthApiResponse;
import com.notare.client.dto.response.VerifyOtpResponse;
import com.notare.client.service.InternalAuthClient;
import com.notare.common.exception.InternalAuthCallException;
import com.notare.common.exception.PasswordMisMatchException;
import com.notare.feature.passwordreset.dto.request.ForgotPasswordRequest;
import com.notare.feature.passwordreset.dto.request.ResetPasswordRequest;
import com.notare.feature.passwordreset.dto.request.VerifyResetOtpRequest;
import com.notare.feature.passwordreset.dto.response.VerifyResetOtpResponse;
import com.notare.feature.passwordreset.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Password reset is owned by internal-auth-server: it emails the code, checks
 * it, issues the reset token and sets the new password. Notare only relays
 * the three steps, so there is no second password store to fall out of sync.
 */
@RequiredArgsConstructor
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private final InternalAuthClient authClient;

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        authClient.requestPasswordResetEmailOtp(request.getEmail().trim());
    }

    @Override
    public VerifyResetOtpResponse verifyResetOtp(VerifyResetOtpRequest request) {
        InternalAuthApiResponse<VerifyOtpResponse> response =
                authClient.verifyPasswordResetEmailOtp(request.getEmail().trim(), request.getCode());

        VerifyOtpResponse data = response == null ? null : response.getData();
        if (data == null || data.getResetToken() == null) {
            throw new InternalAuthCallException(HttpStatus.BAD_GATEWAY, "internal-auth-server did not return a reset token.");
        }
        return new VerifyResetOtpResponse(data.getResetToken());
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new PasswordMisMatchException("Passwords do not match");
        }
        authClient.completeReset(new CompletePasswordResetRequest(
                request.getResetToken(), request.getNewPassword(), request.getConfirmNewPassword()));
    }
}
