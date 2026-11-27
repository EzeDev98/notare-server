package com.notare.feature.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Response to a successful sign-in. */
@Getter
@AllArgsConstructor
public class UserLoginResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private String notarianId;
    private String username;
}
