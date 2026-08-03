package com.notare.feature.passwordreset.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VerifyResetOtpResponse {
    private String resetToken;
}