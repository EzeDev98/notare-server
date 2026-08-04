package com.notare.feature.logout.service.impl;

import com.notare.feature.logout.dto.response.LogoutResponse;
import com.notare.feature.logout.service.LogoutService;
import com.notare.feature.session.service.SessionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutServiceImpl implements LogoutService {

    private final SessionService sessionService;

    @Override
    public LogoutResponse logout(String sessionId, Long userId) {

        if (sessionId == null) {
            return new LogoutResponse(HttpServletResponse.SC_BAD_REQUEST, "SessionId is null");
        }

        sessionService.revokeSession(sessionId, userId);
        return new LogoutResponse(HttpServletResponse.SC_OK, "Logout successfully");
    }

    @Override
    public LogoutResponse logoutAll(Long userId) {
        sessionService.revokeAllSessions(userId);
        return new LogoutResponse(HttpServletResponse.SC_OK, "All account logged out successfully");
    }
}
