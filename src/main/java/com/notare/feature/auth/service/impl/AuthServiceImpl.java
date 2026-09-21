package com.notare.feature.auth.service.impl;

import com.notare.client.dto.response.InternalAuthApiResponse;
import com.notare.client.dto.response.LoginResponse;
import com.notare.client.service.InternalAuthClient;
import com.notare.common.exception.InternalAuthCallException;
import com.notare.feature.auth.dto.request.RefreshTokenRequest;
import com.notare.feature.auth.dto.response.TokenResponse;
import com.notare.feature.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import static com.notare.common.constant.GeneralConstant.TOKEN_TYPE;

/** Token refresh is redeemed at internal-auth-server, the sole session authority. */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final InternalAuthClient authClient;

    @Override
    public TokenResponse refreshToken(RefreshTokenRequest request, String userAgent, String clientIpAddress) {

        InternalAuthApiResponse<LoginResponse> response =
                authClient.refreshToken(new com.notare.client.dto.request.RefreshTokenRequest(request.getRefreshToken()));

        LoginResponse data = response == null ? null : response.getData();
        if (data == null || data.getAccessToken() == null) {
            throw new InternalAuthCallException(HttpStatus.BAD_GATEWAY, "internal-auth-server did not return a session.");
        }

        String refreshToken = data.getRefreshToken() != null ? data.getRefreshToken() : request.getRefreshToken();
        long expiresInMs = data.getExpiresIn() == null ? 0L : data.getExpiresIn() * 1000L;

        return new TokenResponse(data.getAccessToken(), refreshToken, TOKEN_TYPE, expiresInMs);
    }
}
