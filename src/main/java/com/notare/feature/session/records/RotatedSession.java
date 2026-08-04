package com.notare.feature.session.records;

public record RotatedSession(String sessionId, String rawRefreshToken, Long userId) {
}