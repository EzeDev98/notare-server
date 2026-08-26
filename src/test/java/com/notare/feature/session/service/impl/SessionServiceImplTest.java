package com.notare.feature.session.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.exception.InvalidRefreshTokenException;
import com.notare.common.exception.SessionNotFoundException;
import com.notare.feature.session.dto.response.SessionResponse;
import com.notare.feature.session.records.RotatedSession;
import com.notare.feature.session.records.SessionCreationResult;
import com.notare.feature.session.records.SessionRecord;
import com.notare.feature.session.sessionConfig.SessionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 5d of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - Mockito and Spring Data Redis
 * ({@code StringRedisTemplate}, {@code ZSetOperations}) are unavailable as
 * compile-time dependencies here (see {@link
 * com.notare.feature.user.service.impl.UserServiceImplementationTest} for the
 * full explanation). Reasoned carefully from source; run via
 * {@code mvn test} before trusting it.
 *
 * <p>{@link SessionServiceImpl} is the most security-sensitive class covered
 * in this step, so this suite leans hard on its threat model rather than just
 * its happy path:
 *
 * <ul>
 *   <li>Only a SHA-256 hash of the refresh-token secret is ever written to
 *       Redis - never the secret itself. A Redis dump or a compromised cache
 *       operator still cannot mint a session.</li>
 *   <li>The secret comparison in {@code rotateRefreshToken} uses {@code
 *       MessageDigest.isEqual} (constant-time), and a mismatch does not just
 *       reject the request - it revokes the ENTIRE session, on the
 *       assumption that a wrong secret for a real session id means the
 *       stored (rotated) token was stolen and someone is replaying an old
 *       one.</li>
 *   <li>{@code revokeSession} returns the identical exception and message
 *       whether the session id does not exist at all or exists but belongs
 *       to a different user - so a client probing session ids cannot use the
 *       error to tell "wrong id" from "not yours".</li>
 *   <li>{@code touch} refuses to resurrect a session whose TTL has already
 *       lapsed (or is about to - see the null/non-positive check), rather
 *       than silently reviving it because a request happened to race the
 *       expiry.</li>
 * </ul>
 *
 * <p>Where the exact SHA-256 hex digest of a secret matters, this suite
 * computes it locally with the same algorithm the production class uses
 * (plain {@code java.security.MessageDigest}, no third-party dependency) so
 * expectations are not just copy-pasted assumptions.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "user-sessions:";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private SessionProperties sessionProperties;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;

    private SessionServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(sessionProperties.getRefreshTokenExpiryDays()).thenReturn(60);
        lenient().when(sessionProperties.getMaxConcurrentSessions()).thenReturn(5);
        service = new SessionServiceImpl(redisTemplate, objectMapper, sessionProperties);
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
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private SessionRecord record(String sessionId, Long userId, String refreshTokenHash,
                                 Instant createdAt, Instant lastUsedAt, Instant expiresAt) {
        return new SessionRecord(sessionId, userId, refreshTokenHash, "ua", "127.0.0.1",
                createdAt, lastUsedAt, expiresAt);
    }

    // ---- createSession -------------------------------------------------

    @Test
    void createSession_storesOnlyTheSha256HashOfTheSecret_neverTheSecretItself() throws Exception {
        when(objectMapper.writeValueAsString(any(SessionRecord.class))).thenReturn("record-json");
        when(zSetOperations.rangeWithScores(anyString(), eq(0L), eq(-1L))).thenReturn(Set.of());

        SessionCreationResult result = service.createSession(1L, "ua", "127.0.0.1");

        ArgumentCaptor<SessionRecord> captor = ArgumentCaptor.forClass(SessionRecord.class);
        verify(objectMapper).writeValueAsString(captor.capture());
        SessionRecord saved = captor.getValue();

        String rawSecret = result.rawRefreshToken().substring(result.rawRefreshToken().indexOf(':') + 1);
        assertThat(saved.getRefreshTokenHash()).isEqualTo(sha256Hex(rawSecret));
        assertThat(saved.getRefreshTokenHash()).isNotEqualTo(rawSecret);
    }

    @Test
    void createSession_setsTheRedisTtlToTheConfiguredRefreshTokenExpiryDays() throws Exception {
        when(sessionProperties.getRefreshTokenExpiryDays()).thenReturn(60);
        when(objectMapper.writeValueAsString(any(SessionRecord.class))).thenReturn("record-json");
        when(zSetOperations.rangeWithScores(anyString(), eq(0L), eq(-1L))).thenReturn(Set.of());

        service.createSession(1L, "ua", "127.0.0.1");

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(anyString(), eq("record-json"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofDays(60));
    }

    @Test
    void createSession_addsTheSessionToTheUsersZSetScoredByTheCurrentEpochSecond() throws Exception {
        when(objectMapper.writeValueAsString(any(SessionRecord.class))).thenReturn("record-json");
        when(zSetOperations.rangeWithScores(anyString(), eq(0L), eq(-1L))).thenReturn(Set.of());

        SessionCreationResult result = service.createSession(9L, "ua", "127.0.0.1");

        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(zSetOperations).add(eq(USER_SESSIONS_KEY_PREFIX + "9"), eq(result.sessionId()), scoreCaptor.capture());
        assertThat(scoreCaptor.getValue()).isCloseTo((double) Instant.now().getEpochSecond(), within(5.0));
    }

    @Test
    void createSession_returnsARawRefreshTokenShapedAsSessionIdColonSecret() throws Exception {
        when(objectMapper.writeValueAsString(any(SessionRecord.class))).thenReturn("record-json");
        when(zSetOperations.rangeWithScores(anyString(), eq(0L), eq(-1L))).thenReturn(Set.of());

        SessionCreationResult result = service.createSession(1L, "ua", "127.0.0.1");

        assertThat(result.rawRefreshToken()).startsWith(result.sessionId() + ":");
        String secret = result.rawRefreshToken().substring(result.sessionId().length() + 1);
        assertThat(secret).isNotBlank();
    }

    @Test
    void createSession_evictsTheOldestSessionsBeyondTheConcurrentLimit() throws Exception {
        when(sessionProperties.getMaxConcurrentSessions()).thenReturn(2);
        when(objectMapper.writeValueAsString(any(SessionRecord.class))).thenReturn("record-json");
        Set<ZSetOperations.TypedTuple<String>> existing = Set.of(
                new DefaultTypedTuple<>("oldest", 100.0),
                new DefaultTypedTuple<>("middle", 200.0),
                new DefaultTypedTuple<>("newest", 300.0));
        when(zSetOperations.rangeWithScores(anyString(), eq(0L), eq(-1L))).thenReturn(existing);

        service.createSession(1L, "ua", "127.0.0.1");

        // 3 existing + limit 2 = excess 1: only the lowest-scored ("oldest") is evicted.
        verify(redisTemplate).delete(SESSION_KEY_PREFIX + "oldest");
        verify(zSetOperations).remove(USER_SESSIONS_KEY_PREFIX + "1", "oldest");
        verify(redisTemplate, never()).delete(SESSION_KEY_PREFIX + "middle");
        verify(redisTemplate, never()).delete(SESSION_KEY_PREFIX + "newest");
    }

    @Test
    void createSession_evictsNothing_whenAtOrUnderTheConcurrentLimit() throws Exception {
        when(sessionProperties.getMaxConcurrentSessions()).thenReturn(5);
        when(objectMapper.writeValueAsString(any(SessionRecord.class))).thenReturn("record-json");
        Set<ZSetOperations.TypedTuple<String>> existing = Set.of(new DefaultTypedTuple<>("only-one", 100.0));
        when(zSetOperations.rangeWithScores(anyString(), eq(0L), eq(-1L))).thenReturn(existing);

        service.createSession(1L, "ua", "127.0.0.1");

        verify(redisTemplate, never()).delete(SESSION_KEY_PREFIX + "only-one");
    }

    @Test
    void createSession_evictsNothing_whenTheZSetRangeReturnsNull() throws Exception {
        when(objectMapper.writeValueAsString(any(SessionRecord.class))).thenReturn("record-json");
        when(zSetOperations.rangeWithScores(anyString(), eq(0L), eq(-1L))).thenReturn(null);

        // Defensive branch: an unexpected null from Redis must not NPE the whole login.
        service.createSession(1L, "ua", "127.0.0.1");

        verify(redisTemplate, never()).delete(anyString());
    }

    // ---- isSessionActive -------------------------------------------------

    @Test
    void isSessionActive_returnsFalseForANullSessionId_withoutQueryingRedis() {
        assertThat(service.isSessionActive(null)).isFalse();
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    void isSessionActive_returnsTrue_whenRedisReportsTheKeyExists() {
        when(redisTemplate.hasKey(SESSION_KEY_PREFIX + "s1")).thenReturn(true);

        assertThat(service.isSessionActive("s1")).isTrue();
    }

    @Test
    void isSessionActive_returnsFalse_whenRedisReportsTheKeyIsAbsentOrNull() {
        when(redisTemplate.hasKey(SESSION_KEY_PREFIX + "s1")).thenReturn(false);
        assertThat(service.isSessionActive("s1")).isFalse();

        when(redisTemplate.hasKey(SESSION_KEY_PREFIX + "s2")).thenReturn(null);
        assertThat(service.isSessionActive("s2")).isFalse();
    }

    // ---- touch -------------------------------------------------

    @Test
    void touch_doesNothing_whenTheSessionRecordIsAbsent() {
        when(valueOperations.get(SESSION_KEY_PREFIX + "gone")).thenReturn(null);

        service.touch("gone");

        verify(redisTemplate, never()).getExpire(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void touch_doesNothing_whenTheTtlIsNullOrNonPositive() throws Exception {
        when(valueOperations.get(SESSION_KEY_PREFIX + "s1")).thenReturn("record-json");
        SessionRecord existing = record("s1", 1L, "hash", Instant.now(), Instant.now(), Instant.now());
        when(objectMapper.readValue("record-json", SessionRecord.class)).thenReturn(existing);
        when(redisTemplate.getExpire(SESSION_KEY_PREFIX + "s1")).thenReturn(null);

        service.touch("s1");

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));

        when(redisTemplate.getExpire(SESSION_KEY_PREFIX + "s1")).thenReturn(0L);
        service.touch("s1");
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void touch_updatesLastUsedAtAndResavesWithTheSameRemainingTtl() throws Exception {
        when(valueOperations.get(SESSION_KEY_PREFIX + "s1")).thenReturn("record-json");
        SessionRecord existing = record("s1", 1L, "hash",
                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(3600), Instant.now().plusSeconds(1000));
        when(objectMapper.readValue("record-json", SessionRecord.class)).thenReturn(existing);
        when(redisTemplate.getExpire(SESSION_KEY_PREFIX + "s1")).thenReturn(1000L);
        when(objectMapper.writeValueAsString(existing)).thenReturn("resaved-json");

        Instant before = existing.getLastUsedAt();
        service.touch("s1");

        assertThat(existing.getLastUsedAt()).isAfter(before);
        verify(valueOperations).set(SESSION_KEY_PREFIX + "s1", "resaved-json", Duration.ofSeconds(1000));
    }

    // ---- rotateRefreshToken -------------------------------------------------

    @Test
    void rotateRefreshToken_throwsInvalidRefreshTokenException_whenTheTokenIsNull() {
        assertThatThrownBy(() -> service.rotateRefreshToken(null, "ua", "ip"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token is required.");
    }

    @Test
    void rotateRefreshToken_throwsInvalidRefreshTokenException_whenThereIsNoColon() {
        assertThatThrownBy(() -> service.rotateRefreshToken("nocolonhere", "ua", "ip"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Malformed refresh token.");
    }

    @Test
    void rotateRefreshToken_throwsInvalidRefreshTokenException_whenTheColonIsTheFirstCharacter() {
        assertThatThrownBy(() -> service.rotateRefreshToken(":secretonly", "ua", "ip"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Malformed refresh token.");
    }

    @Test
    void rotateRefreshToken_throwsInvalidRefreshTokenException_whenTheColonIsTheLastCharacter() {
        assertThatThrownBy(() -> service.rotateRefreshToken("sessionidonly:", "ua", "ip"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Malformed refresh token.");
    }

    @Test
    void rotateRefreshToken_throwsInvalidRefreshTokenException_whenTheSessionRecordIsAbsent() {
        when(valueOperations.get(SESSION_KEY_PREFIX + "s1")).thenReturn(null);

        assertThatThrownBy(() -> service.rotateRefreshToken("s1:secret", "ua", "ip"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Session no longer valid.");
    }

    @Test
    void rotateRefreshToken_treatsACorruptStoredRecord_asIfTheSessionDidNotExist() throws Exception {
        when(valueOperations.get(SESSION_KEY_PREFIX + "s1")).thenReturn("not-valid-json");
        when(objectMapper.readValue("not-valid-json", SessionRecord.class))
                .thenThrow(new RuntimeException("corrupt"));

        assertThatThrownBy(() -> service.rotateRefreshToken("s1:secret", "ua", "ip"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Session no longer valid.");

        // Corrupt entries are cleaned up on read, not left to fail the same way again.
        verify(redisTemplate).delete(SESSION_KEY_PREFIX + "s1");
    }

    @Test
    void rotateRefreshToken_onASecretMismatch_deletesTheEntireSessionAndThrows() throws Exception {
        when(valueOperations.get(SESSION_KEY_PREFIX + "s1")).thenReturn("record-json");
        SessionRecord stored = record("s1", 7L, sha256Hex("correct-secret"),
                Instant.now(), Instant.now(), Instant.now().plusSeconds(1000));
        when(objectMapper.readValue("record-json", SessionRecord.class)).thenReturn(stored);

        assertThatThrownBy(() -> service.rotateRefreshToken("s1:wrong-secret", "ua", "ip"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token invalid; session revoked.");

        verify(redisTemplate).delete(SESSION_KEY_PREFIX + "s1");
        verify(zSetOperations).remove(USER_SESSIONS_KEY_PREFIX + "7", "s1");
    }

    @Test
    void rotateRefreshToken_onAMatch_rotatesTheSecretAndReturnsANewRawRefreshToken() throws Exception {
        when(valueOperations.get(SESSION_KEY_PREFIX + "s1")).thenReturn("record-json");
        SessionRecord stored = record("s1", 7L, sha256Hex("correct-secret"),
                Instant.now(), Instant.now(), Instant.now().plusSeconds(1000));
        when(objectMapper.readValue("record-json", SessionRecord.class)).thenReturn(stored);
        when(objectMapper.writeValueAsString(stored)).thenReturn("rotated-json");

        RotatedSession result = service.rotateRefreshToken("s1:correct-secret", "new-ua", "new-ip");

        assertThat(result.sessionId()).isEqualTo("s1");
        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.rawRefreshToken()).startsWith("s1:");
        String newSecret = result.rawRefreshToken().substring("s1:".length());
        assertThat(newSecret).isNotEqualTo("correct-secret");

        // The record itself was mutated in place to the new hash/metadata before saving.
        assertThat(stored.getRefreshTokenHash()).isEqualTo(sha256Hex(newSecret));
        assertThat(stored.getUserAgent()).isEqualTo("new-ua");
        assertThat(stored.getIpAddress()).isEqualTo("new-ip");
        verify(valueOperations).set(eq(SESSION_KEY_PREFIX + "s1"), eq("rotated-json"), any(Duration.class));
    }

    @Test
    void rotateRefreshToken_doesNotDeleteTheSession_onASuccessfulRotation() throws Exception {
        when(valueOperations.get(SESSION_KEY_PREFIX + "s1")).thenReturn("record-json");
        SessionRecord stored = record("s1", 7L, sha256Hex("correct-secret"),
                Instant.now(), Instant.now(), Instant.now().plusSeconds(1000));
        when(objectMapper.readValue("record-json", SessionRecord.class)).thenReturn(stored);
        when(objectMapper.writeValueAsString(stored)).thenReturn("rotated-json");

        service.rotateRefreshToken("s1:correct-secret", "ua", "ip");

        verify(redisTemplate, never()).delete(anyString());
    }

    // ---- revokeSession -------------------------------------------------

    @Test
    void revokeSession_deletesTheSession_whenItBelongsToTheRequestingUser() throws Exception {
        when(valueOperations.get(SESSION_KEY_PREFIX + "s1")).thenReturn("record-json");
        SessionRecord stored = record("s1", 7L, "hash", Instant.now(), Instant.now(), Instant.now().plusSeconds(1000));
        when(objectMapper.readValue("record-json", SessionRecord.class)).thenReturn(stored);

        service.revokeSession("s1", 7L);

        verify(redisTemplate).delete(SESSION_KEY_PREFIX + "s1");
        verify(zSetOperations).remove(USER_SESSIONS_KEY_PREFIX + "7", "s1");
    }

    @Test
    void revokeSession_throwsSessionNotFoundException_whenTheSessionDoesNotExist() {
        when(valueOperations.get(SESSION_KEY_PREFIX + "s1")).thenReturn(null);

        assertThatThrownBy(() -> service.revokeSession("s1", 7L))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessage("Session not found.");
    }

    @Test
    void revokeSession_throwsTheIdenticalExceptionAndMessage_whenTheSessionBelongsToSomeoneElse() throws Exception {
        // No information leak: "belongs to someone else" must look exactly like
        // "doesn't exist" to whoever is calling with a guessed session id.
        when(valueOperations.get(SESSION_KEY_PREFIX + "s1")).thenReturn("record-json");
        SessionRecord stored = record("s1", 999L, "hash", Instant.now(), Instant.now(), Instant.now().plusSeconds(1000));
        when(objectMapper.readValue("record-json", SessionRecord.class)).thenReturn(stored);

        assertThatThrownBy(() -> service.revokeSession("s1", 7L))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessage("Session not found.");

        // And critically: the session belonging to user 999 is left untouched.
        verify(redisTemplate, never()).delete(anyString());
    }

    // ---- revokeAllSessions -------------------------------------------------

    @Test
    void revokeAllSessions_deletesEverySessionKeyInTheUsersZSet_andTheZSetItself() {
        when(zSetOperations.range(USER_SESSIONS_KEY_PREFIX + "5", 0, -1))
                .thenReturn(Set.of("s1", "s2", "s3"));

        service.revokeAllSessions(5L);

        verify(redisTemplate).delete(SESSION_KEY_PREFIX + "s1");
        verify(redisTemplate).delete(SESSION_KEY_PREFIX + "s2");
        verify(redisTemplate).delete(SESSION_KEY_PREFIX + "s3");
        verify(redisTemplate).delete(USER_SESSIONS_KEY_PREFIX + "5");
    }

    @Test
    void revokeAllSessions_stillDeletesTheZSetKey_whenThereAreNoSessions() {
        when(zSetOperations.range(USER_SESSIONS_KEY_PREFIX + "5", 0, -1)).thenReturn(null);

        service.revokeAllSessions(5L);

        verify(redisTemplate).delete(USER_SESSIONS_KEY_PREFIX + "5");
        verify(redisTemplate, times(1)).delete(anyString());
    }

    // ---- listSessions -------------------------------------------------

    @Test
    void listSessions_returnsAnEmptyList_whenTheUserHasNoSessions() {
        when(zSetOperations.range(USER_SESSIONS_KEY_PREFIX + "5", 0, -1)).thenReturn(Set.of());

        assertThat(service.listSessions(5L, "current")).isEmpty();
    }

    @Test
    void listSessions_selfHealsByPruningAStaleZSetEntryForAnExpiredSession() throws Exception {
        when(zSetOperations.range(USER_SESSIONS_KEY_PREFIX + "5", 0, -1)).thenReturn(Set.of("expired", "alive"));
        when(valueOperations.get(SESSION_KEY_PREFIX + "expired")).thenReturn(null);
        when(valueOperations.get(SESSION_KEY_PREFIX + "alive")).thenReturn("record-json");
        SessionRecord aliveRecord = record("alive", 5L, "hash", Instant.now(), Instant.now(), Instant.now().plusSeconds(1000));
        when(objectMapper.readValue("record-json", SessionRecord.class)).thenReturn(aliveRecord);

        List<SessionResponse> result = service.listSessions(5L, "current");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSessionId()).isEqualTo("alive");
        verify(zSetOperations).remove(USER_SESSIONS_KEY_PREFIX + "5", "expired");
    }

    @Test
    void listSessions_sortsByLastUsedAtDescending() throws Exception {
        when(zSetOperations.range(USER_SESSIONS_KEY_PREFIX + "5", 0, -1)).thenReturn(Set.of("older", "newer"));
        Instant now = Instant.now();
        when(valueOperations.get(SESSION_KEY_PREFIX + "older")).thenReturn("older-json");
        when(valueOperations.get(SESSION_KEY_PREFIX + "newer")).thenReturn("newer-json");
        when(objectMapper.readValue("older-json", SessionRecord.class))
                .thenReturn(record("older", 5L, "hash", now.minusSeconds(200), now.minusSeconds(200), now.plusSeconds(1000)));
        when(objectMapper.readValue("newer-json", SessionRecord.class))
                .thenReturn(record("newer", 5L, "hash", now.minusSeconds(10), now.minusSeconds(10), now.plusSeconds(1000)));

        List<SessionResponse> result = service.listSessions(5L, "current");

        assertThat(result).extracting(SessionResponse::getSessionId).containsExactly("newer", "older");
    }

    @Test
    void listSessions_flagsExactlyTheSessionMatchingTheCurrentSessionId() throws Exception {
        when(zSetOperations.range(USER_SESSIONS_KEY_PREFIX + "5", 0, -1)).thenReturn(Set.of("this-one", "other"));
        Instant now = Instant.now();
        when(valueOperations.get(SESSION_KEY_PREFIX + "this-one")).thenReturn("this-json");
        when(valueOperations.get(SESSION_KEY_PREFIX + "other")).thenReturn("other-json");
        when(objectMapper.readValue("this-json", SessionRecord.class))
                .thenReturn(record("this-one", 5L, "hash", now, now, now.plusSeconds(1000)));
        when(objectMapper.readValue("other-json", SessionRecord.class))
                .thenReturn(record("other", 5L, "hash", now, now.minusSeconds(1), now.plusSeconds(1000)));

        List<SessionResponse> result = service.listSessions(5L, "this-one");

        assertThat(result).filteredOn(r -> r.getSessionId().equals("this-one"))
                .extracting(SessionResponse::isCurrent).containsExactly(true);
        assertThat(result).filteredOn(r -> r.getSessionId().equals("other"))
                .extracting(SessionResponse::isCurrent).containsExactly(false);
    }
}