package com.notare.feature.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** A new access and refresh token pair. */
@Getter
@AllArgsConstructor
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
}
