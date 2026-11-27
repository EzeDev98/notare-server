package com.notare.client.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body sent to internal-auth-server to set a new password. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompletePasswordResetRequest {
    private String resetToken;
    private String newPassword;
    private String confirmPassword;
}
