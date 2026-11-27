package com.notare.client.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** internal-auth-server's answer to a one-time code check. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VerifyOtpResponse {
    private String resetToken;
}
