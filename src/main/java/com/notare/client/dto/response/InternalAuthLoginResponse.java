package com.notare.client.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** internal-auth-server's answer to a sign-in. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InternalAuthLoginResponse {
    private Long userId;
    private String username;
    private String message;
    private boolean mfaRequired;
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private Long refreshExpiresIn;
}
