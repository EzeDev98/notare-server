package com.notare.common.ratelimit;

import com.notare.common.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed fixed-window counter, one per (bucket, user), one minute wide (step 17j, B-15).
 *
 * <p>The window opens on the first request and the key expires with it. A burst at the very end of
 * one window and the start of the next can pass up to twice the limit, which is fine for abuse
 * protection and keeps this to one INCR. If Redis cannot be reached the request is let through:
 * a limiter must not take the API down with it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final String KEY_PREFIX = "rate-limit:";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;

    /** Counts one request and throws {@link TooManyRequestsException} once the user is over the limit. */
    public void check(String bucket, Long userId, int limitPerMinute) {
        String key = KEY_PREFIX + bucket + ":" + userId;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return;
            }
            if (count == 1) {
                redis.expire(key, WINDOW);
            }
            if (count > limitPerMinute) {
                throw new TooManyRequestsException(secondsLeft(key));
            }
        } catch (DataAccessException ex) {
            log.warn("Rate limiter unavailable, letting the request through: {}", ex.getMessage());
        }
    }

    /** Seconds until the window closes. A key that lost its expiry (a crash between INCR and EXPIRE) gets a fresh one. */
    private long secondsLeft(String key) {
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            redis.expire(key, WINDOW);
            return WINDOW.toSeconds();
        }
        return Math.max(ttl, 1);
    }
}
