package com.notare.feature.session.records;

import java.time.Instant;

public record SessionSummary(String sessionId, String userAgent, String ipAddress,
                             Instant createdAt, Instant lastUsedAt, Instant expiresAt) {
}