package com.notare.feature.logout.service.impl;

import com.notare.client.service.InternalAuthClient;
import com.notare.feature.logout.dto.response.LogoutResponse;
import com.notare.feature.logout.service.LogoutService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Ends a session through internal-auth-server. */
@Service
@RequiredArgsConstructor
public class LogoutServiceImpl implements LogoutService {

    private final InternalAuthClient authClient;

    @Override
    public LogoutResponse logout(String accessToken) {

        if (accessToken == null || accessToken.isBlank()) {
            return new LogoutResponse(HttpServletResponse.SC_BAD_REQUEST, "Access token is missing");
        }

        authClient.logout(accessToken);
        return new LogoutResponse(HttpServletResponse.SC_OK, "Logout successfully");
    }
}
