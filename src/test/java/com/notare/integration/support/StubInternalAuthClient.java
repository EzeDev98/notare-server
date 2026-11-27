package com.notare.integration.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.client.dto.request.LoginRequest;
import com.notare.client.dto.request.RefreshTokenRequest;
import com.notare.client.dto.request.RegisterRequest;
import com.notare.client.dto.response.InternalAuthApiResponse;
import com.notare.client.dto.response.InternalAuthLoginResponse;
import com.notare.client.dto.response.LoginResponse;
import com.notare.client.dto.response.LogoutResponse;
import com.notare.client.dto.response.RegisterResponse;
import com.notare.client.service.InternalAuthClient;
import com.notare.common.exception.InternalAuthCallException;
import com.notare.common.exception.RefreshTokenInvalidException;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Test stand-in used by the integration tests. */
@Component
@Primary
@Profile("test")
public class StubInternalAuthClient extends InternalAuthClient {

    private final Map<String, String> passwords = new ConcurrentHashMap<>();
    private final Map<String, String> accessTokens = new ConcurrentHashMap<>();
    private final Map<String, String> refreshTokens = new ConcurrentHashMap<>();

    public StubInternalAuthClient() {
        super("http://localhost:5151/api/v1", "notare", "test-client-secret", "", new ObjectMapper());
    }

    @Override
    public InternalAuthApiResponse<RegisterResponse> register(RegisterRequest request) {
        passwords.put(key(request.getUsername()), request.getPassword());
        return InternalAuthApiResponse.success("ok",
                new RegisterResponse(UUID.randomUUID().toString(), request.getUsername(), "ok"));
    }

    @Override
    public InternalAuthApiResponse<InternalAuthLoginResponse> login(LoginRequest request) {
        String expected = passwords.get(key(request.getUsername()));
        if (expected == null || !expected.equals(request.getPassword())) {
            throw new InternalAuthCallException(HttpStatus.UNAUTHORIZED, "Username or password is incorrect");
        }
        String[] tokens = mint(key(request.getUsername()));
        return InternalAuthApiResponse.success("Login successful.",
                new InternalAuthLoginResponse(1L, request.getUsername(), "Login successful.", false,
                        tokens[0], tokens[1], 900L, 2592000L));
    }

    @Override
    public InternalAuthApiResponse<LoginResponse> refreshToken(RefreshTokenRequest request) {
        String username = request.getRefreshToken() == null ? null : refreshTokens.remove(request.getRefreshToken());
        if (username == null) {
            throw new RefreshTokenInvalidException("Refresh token is invalid or expired");
        }
        String[] tokens = mint(username);
        return InternalAuthApiResponse.success("Token refreshed.",
                new LoginResponse(1L, username, "Token refreshed.", false, tokens[0], tokens[1], 900L, 2592000L));
    }

    @Override
    public InternalAuthApiResponse<LogoutResponse> logout(String accessToken) {
        String username = accessTokens.remove(accessToken);
        if (username != null) {
            refreshTokens.values().removeIf(username::equals);
        }
        return InternalAuthApiResponse.success("ok", new LogoutResponse("ok", 200));
    }

    @Override
    public void deleteUser(String internalAuthUserId) {
    }

    public String usernameForActiveToken(String accessToken) {
        return accessToken == null ? null : accessTokens.get(accessToken);
    }

    private String[] mint(String username) {
        String access = "stub-access-" + UUID.randomUUID();
        String refresh = "stub-refresh-" + UUID.randomUUID();
        accessTokens.put(access, username);
        refreshTokens.put(refresh, username);
        return new String[]{access, refresh};
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
