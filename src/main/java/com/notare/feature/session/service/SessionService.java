package com.notare.feature.session.service;

import com.notare.client.dto.response.SessionResponse;

import java.util.List;

public interface SessionService {

    List<SessionResponse> listSessions(String accessToken);

    void revokeSession(String accessToken, String sessionId);
}
