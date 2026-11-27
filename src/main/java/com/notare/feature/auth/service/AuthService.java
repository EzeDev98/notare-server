package com.notare.feature.auth.service;

import com.notare.feature.auth.dto.request.RefreshTokenRequest;
import com.notare.feature.auth.dto.response.TokenResponse;

/** Refreshes sessions. */
public interface AuthService {
    TokenResponse refreshToken(RefreshTokenRequest request, String userAgent, String clientIpAddress);
}
