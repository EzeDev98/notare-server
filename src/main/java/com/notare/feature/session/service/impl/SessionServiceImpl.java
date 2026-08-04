package com.notare.feature.session.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.exception.InvalidRefreshTokenException;
import com.notare.common.exception.SessionNotFoundException;
import com.notare.feature.session.dto.response.SessionResponse;
import com.notare.feature.session.records.RotatedSession;
import com.notare.feature.session.records.SessionCreationResult;
import com.notare.feature.session.records.SessionRecord;
import com.notare.feature.session.records.SessionSummary;
import com.notare.feature.session.service.SessionService;
import com.notare.feature.session.sessionConfig.SessionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "user-sessions:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SessionProperties sessionProperties;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public SessionCreationResult createSession(Long userId, String userAgent, String ipAddress) {

        String sessionId = UUID.randomUUID().toString();
        String secret = generateSecret();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofDays(sessionProperties.getRefreshTokenExpiryDays()));

        SessionRecord record = new SessionRecord(sessionId, userId, sha256Hex(secret), userAgent, ipAddress, now, now, expiresAt);

        saveRecord(record, Duration.between(now, expiresAt));
        redisTemplate.opsForZSet().add(userSessionsKey(userId), sessionId, now.getEpochSecond());

        enforceConcurrentSessionLimit(userId);

        return new SessionCreationResult(sessionId, sessionId + ":" + secret);
    }

    @Override
    public boolean isSessionActive(String sessionId) {
        return sessionId != null && Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(sessionId)));
    }

    @Override
    public void touch(String sessionId) {
        readRecord(sessionId).ifPresent(record -> {
            Long ttlSeconds = redisTemplate.getExpire(sessionKey(sessionId));
            if (ttlSeconds == null || ttlSeconds <= 0) {
                return;
            }
            record.setLastUsedAt(Instant.now());
            saveRecord(record, Duration.ofSeconds(ttlSeconds));
        });
    }

    @Override
    public RotatedSession rotateRefreshToken(String rawRefreshToken, String userAgent, String ipAddress) {

        String[] parts = splitToken(rawRefreshToken);
        String sessionId = parts[0];
        String presentedSecret = parts[1];

        SessionRecord record = readRecord(sessionId)
                .orElseThrow(() -> new InvalidRefreshTokenException("Session no longer valid."));

        if (!constantTimeEquals(sha256Hex(presentedSecret), record.getRefreshTokenHash())) {
            // Presented secret doesn't match what we have on file for this session ? possible
            // theft or reuse of an already-rotated token. Kill the session outright.
            log.warn("Refresh token mismatch for session {} ? revoking session.", sessionId);
            deleteRecord(sessionId, record.getUserId());
            throw new InvalidRefreshTokenException("Refresh token invalid; session revoked.");
        }

        String newSecret = generateSecret();
        Instant now = Instant.now();
        Instant newExpiresAt = now.plus(Duration.ofDays(sessionProperties.getRefreshTokenExpiryDays()));

        record.setRefreshTokenHash(sha256Hex(newSecret));
        record.setLastUsedAt(now);
        record.setExpiresAt(newExpiresAt);
        record.setUserAgent(userAgent);
        record.setIpAddress(ipAddress);

        saveRecord(record, Duration.between(now, newExpiresAt));

        return new RotatedSession(sessionId, sessionId + ":" + newSecret, record.getUserId());
    }

    @Override
    public void revokeSession(String sessionId, Long requestingUserId) {
        SessionRecord record = readRecord(sessionId)
                .filter(r -> r.getUserId().equals(requestingUserId))
                .orElseThrow(() -> new SessionNotFoundException("Session not found."));

        deleteRecord(sessionId, requestingUserId);
    }

    @Override
    public void revokeAllSessions(Long userId) {
        String userSessionsKey = userSessionsKey(userId);
        Set<String> sessionIds = redisTemplate.opsForZSet().range(userSessionsKey, 0, -1);

        if (sessionIds != null) {
            sessionIds.forEach(id -> redisTemplate.delete(sessionKey(id)));
        }
        redisTemplate.delete(userSessionsKey);
    }

    @Override
    public List<SessionResponse> listSessions(Long userId, String currentSessionId) {
        String userSessionsKey = userSessionsKey(userId);
        Set<String> sessionIds = redisTemplate.opsForZSet().range(userSessionsKey, 0, -1);

        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }

        List<SessionSummary> summaries = new ArrayList<>();
        for (String sessionId : sessionIds) {
            Optional<SessionRecord> record = readRecord(sessionId);
            if (record.isPresent()) {
                SessionRecord r = record.get();
                summaries.add(new SessionSummary(r.getSessionId(), r.getUserAgent(), r.getIpAddress(),
                        r.getCreatedAt(), r.getLastUsedAt(), r.getExpiresAt()));
            } else {
                // Session key expired/was removed but the index entry lingers; prune it.
                redisTemplate.opsForZSet().remove(userSessionsKey, sessionId);
            }
        }

        summaries.sort(Comparator.comparing(SessionSummary::lastUsedAt).reversed());
        return toResponse(summaries, currentSessionId);
    }

    private List<SessionResponse> toResponse(List<SessionSummary> summaries, String currentSessionId) {

        List<SessionResponse> responses = new ArrayList<>();

        for (SessionSummary summary : summaries) {
            SessionResponse response = new SessionResponse(summary.sessionId(), summary.userAgent(), summary.ipAddress(),
                    summary.createdAt(), summary.lastUsedAt(), summary.expiresAt(), summary.sessionId().equals(currentSessionId));
            responses.add(response);
        }

        return responses;
    }

    private void enforceConcurrentSessionLimit(Long userId) {
        String userSessionsKey = userSessionsKey(userId);
        Set<ZSetOperations.TypedTuple<String>> all = redisTemplate.opsForZSet().rangeWithScores(userSessionsKey, 0, -1);

        if (all == null) {
            return;
        }

        int excess = all.size() - sessionProperties.getMaxConcurrentSessions();
        if (excess <= 0) {
            return;
        }

        all.stream()
                .filter(tuple -> tuple.getValue() != null)
                .sorted(Comparator.comparing(ZSetOperations.TypedTuple::getScore))
                .limit(excess)
                .map(ZSetOperations.TypedTuple::getValue)
                .forEach(sessionId -> {
                    redisTemplate.delete(sessionKey(sessionId));
                    redisTemplate.opsForZSet().remove(userSessionsKey, sessionId);
                });
    }

    private void deleteRecord(String sessionId, Long userId) {
        redisTemplate.delete(sessionKey(sessionId));
        redisTemplate.opsForZSet().remove(userSessionsKey(userId), sessionId);
    }

    private void saveRecord(SessionRecord record, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(sessionKey(record.getSessionId()), json, ttl);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist session.", ex);
        }
    }

    private Optional<SessionRecord> readRecord(String sessionId) {
        String json = redisTemplate.opsForValue().get(sessionKey(sessionId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, SessionRecord.class));
        } catch (Exception ex) {
            log.warn("Corrupt session record for {}, dropping it.", sessionId);
            redisTemplate.delete(sessionKey(sessionId));
            return Optional.empty();
        }
    }

    private String[] splitToken(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            throw new InvalidRefreshTokenException("Refresh token is required.");
        }
        int separatorIndex = rawRefreshToken.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex == rawRefreshToken.length() - 1) {
            throw new InvalidRefreshTokenException("Malformed refresh token.");
        }
        return new String[]{rawRefreshToken.substring(0, separatorIndex), rawRefreshToken.substring(separatorIndex + 1)};
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available.", ex);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private String userSessionsKey(Long userId) {
        return USER_SESSIONS_KEY_PREFIX + userId;
    }
}