package com.notare.feature.collab.ticket;

import com.notare.feature.collab.enums.CollabRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Tickets in Redis, as {@code collab:ticket:<ticket>} = {@code userId:noteId:ROLE}.
 *
 * <p>Redeem uses GETDEL, which reads and deletes atomically. Two handshakes
 * racing with the same ticket cannot both win, which is what "single use"
 * needs; a get followed by a delete would let both through.
 *
 * <p>The ticket itself is never logged. It is a credential for 60 seconds.
 */
@Component
@RequiredArgsConstructor
class RedisCollabTicketStore implements CollabTicketStore {

    private static final String KEY_PREFIX = "collab:ticket:";

    private final StringRedisTemplate redis;

    @Override
    public void put(String ticket, CollabTicket value, Duration ttl) {
        String stored = value.userId() + ":" + value.noteId() + ":" + value.role().name();
        redis.opsForValue().set(KEY_PREFIX + ticket, stored, ttl);
    }

    @Override
    public Optional<CollabTicket> redeem(String ticket) {
        String stored = redis.opsForValue().getAndDelete(KEY_PREFIX + ticket);
        if (stored == null) {
            return Optional.empty();
        }
        String[] parts = stored.split(":");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CollabTicket(
                    Long.valueOf(parts[0]),
                    Long.valueOf(parts[1]),
                    CollabRole.valueOf(parts[2])));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
