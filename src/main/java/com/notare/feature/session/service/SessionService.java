package com.notare.feature.session.service;

import com.notare.feature.session.dto.response.SessionResponse;
import com.notare.feature.session.records.RotatedSession;
import com.notare.feature.session.records.SessionCreationResult;
import com.notare.feature.session.records.SessionSummary;

import java.util.List;

public interface SessionService {

    /** Creates a new session for a freshly authenticated user, evicting the oldest session if the user is over their concurrent-session limit. */
    SessionCreationResult createSession(Long userId, String userAgent, String ipAddress);

    /** Whether a session is still active (used by JwtAuthenticationFilter to allow instant revocation of otherwise-stateless access tokens). */
    boolean isSessionActive(String sessionId);

    /** Updates last-used-at for a session; best-effort, called on each authenticated request. */
    void touch(String sessionId);

    /**
     * Validates a presented refresh token against the session it claims to belong to and rotates it.
     * A mismatch (reused/stolen token) revokes the session outright.
     */
    RotatedSession rotateRefreshToken(String rawRefreshToken, String userAgent, String ipAddress);

    /** Revokes one session. Throws if it doesn't exist or doesn't belong to the requesting user. */
    void revokeSession(String sessionId, Long requestingUserId);

    /** Revokes every session belonging to a user. */
    void revokeAllSessions(Long userId);

    /** Lists all active sessions for a user, most recently used first. */
    List<SessionResponse> listSessions(Long userId, String currentSessionId);
}