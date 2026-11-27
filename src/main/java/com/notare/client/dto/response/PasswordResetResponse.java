package com.notare.client.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** internal-auth-server's answer to a password-reset request. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PasswordResetResponse {
    private String message;
    private int statusCode;
    private String phoneNumber;
}
