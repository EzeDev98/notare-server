package com.notare.feature.session.records;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionRecord {

    private String sessionId;
    private Long userId;
    private String refreshTokenHash;
    private String userAgent;
    private String ipAddress;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;
}