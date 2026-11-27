package com.notare.common.ratelimit;

import com.notare.common.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Redis-backed per-user request counter. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final String KEY_PREFIX = "rate-limit:";
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;

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

    private long secondsLeft(String key) {
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            redis.expire(key, WINDOW);
            return WINDOW.toSeconds();
        }
        return Math.max(ttl, 1);
    }
}
