package com.notare.feature.auth.service.impl;

import com.notare.client.dto.response.InternalAuthApiResponse;
import com.notare.client.dto.response.LoginResponse;
import com.notare.client.service.InternalAuthClient;
import com.notare.common.exception.InternalAuthCallException;
import com.notare.common.exception.RefreshTokenInvalidException;
import com.notare.feature.auth.dto.request.RefreshTokenRequest;
import com.notare.feature.auth.dto.response.TokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Tests for AuthServiceImpl. */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private InternalAuthClient authClient;
    @InjectMocks private AuthServiceImpl service;

    private RefreshTokenRequest request(String token) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token);
        return request;
    }

    private InternalAuthApiResponse<LoginResponse> iam(String access, String refresh) {
        return InternalAuthApiResponse.success("ok",
                new LoginResponse(1L, "jdoe", "ok", false, access, refresh, 900L, 2592000L));
    }

    @Test
    void refreshToken_returnsTheIamTokens_withExpiryInMilliseconds() {
        when(authClient.refreshToken(any())).thenReturn(iam("access-2", "refresh-2"));

        TokenResponse response = service.refreshToken(request("refresh-1"), "UA", "ip");

        assertThat(response.getAccessToken()).isEqualTo("access-2");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-2");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900_000L);
    }

    @Test
    void refreshToken_keepsTheCallersRefreshToken_whenIamDoesNotRotateIt() {
        when(authClient.refreshToken(any())).thenReturn(iam("access-2", null));

        TokenResponse response = service.refreshToken(request("refresh-1"), "UA", "ip");

        assertThat(response.getRefreshToken()).isEqualTo("refresh-1");
    }

    @Test
    void refreshToken_rejectedByIam_propagatesRefreshTokenInvalid() {
        when(authClient.refreshToken(any())).thenThrow(new RefreshTokenInvalidException("Refresh token is invalid or expired"));

        assertThatThrownBy(() -> service.refreshToken(request("bad"), "UA", "ip"))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void refreshToken_emptyIamBody_isReportedAsBadGateway() {
        when(authClient.refreshToken(any())).thenReturn(null);

        assertThatThrownBy(() -> service.refreshToken(request("refresh-1"), "UA", "ip"))
                .isInstanceOf(InternalAuthCallException.class);
    }
}
