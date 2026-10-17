package com.notare.feature.session.service.impl;

import com.notare.client.dto.response.InternalAuthApiResponse;
import com.notare.client.dto.response.SessionResponse;
import com.notare.client.service.InternalAuthClient;
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

    /** Signs out the chosen device only; the other devices stay signed in. 404 if it is not the caller's. */
    @Override
    public void revokeSession(String accessToken, String sessionId) {
        authClient.revokeSession(accessToken, sessionId);
    }
}
