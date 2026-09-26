package com.notare.feature.session.service.impl;

import com.notare.client.dto.response.InternalAuthApiResponse;
import com.notare.client.dto.response.SessionResponse;
import com.notare.client.service.InternalAuthClient;
import com.notare.common.exception.SessionNotFoundException;
import com.notare.feature.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final InternalAuthClient authClient;

    @Override
    public List<SessionResponse> listSessions(String accessToken) {
        InternalAuthApiResponse<List<SessionResponse>> response = authClient.listSessions(accessToken);
        return response == null || response.getData() == null ? List.of() : response.getData();
    }

    /**
     * internal-auth keeps ONE active session per user, so the only session
     * that can be revoked here is the caller's own: confirm the id belongs to
     * it, then log it out.
     */
    @Override
    public void revokeSession(String accessToken, String sessionId) {
        boolean known = listSessions(accessToken).stream()
                .anyMatch(session -> session.getSessionId().equals(sessionId));
        if (!known) {
            throw new SessionNotFoundException("Session not found");
        }
        authClient.logout(accessToken);
    }
}
