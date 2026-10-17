package com.notare.client.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One signed-in session, in the shape the web client's Sessions page reads.
 * internal-auth owns sessions; this is its answer to GET /session, passed
 * through unchanged.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {
    private String sessionId;
    private String deviceLabel;
    private String userAgent;
    private String ipAddress;
    private Instant createdAt;
    private Instant lastActiveAt;
    private Instant expiresAt;
    private boolean current;
}
