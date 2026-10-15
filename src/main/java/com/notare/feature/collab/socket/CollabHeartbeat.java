package com.notare.feature.collab.socket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * Keeps sockets alive and clears the dead ones (step 15c).
 *
 * <p>Render closes idle connections, so the server pings every 25 seconds. A
 * browser answers a ping with a pong by itself. A session that has said nothing
 * (no frame, no pong) for 60 seconds, about two missed pings, is closed and
 * forgotten, which also frees whatever it held.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollabHeartbeat {

    static final long PING_EVERY_MS = 25_000;
    static final long DEAD_AFTER_MS = 60_000;

    private final CollabSessions sessions;

    @Scheduled(fixedDelay = PING_EVERY_MS, initialDelay = PING_EVERY_MS)
    void pingAll() {
        long now = System.currentTimeMillis();

        for (WebSocketSession session : sessions.all()) {
            if (!session.isOpen()) {
                sessions.remove(session.getId());
                continue;
            }

            if (now - CollabAttributes.lastSeen(session) > DEAD_AFTER_MS) {
                drop(session, CloseStatus.SESSION_NOT_RELIABLE);
                continue;
            }

            try {
                session.sendMessage(new PingMessage());
            } catch (IOException | RuntimeException failed) {
                drop(session, CloseStatus.SESSION_NOT_RELIABLE);
            }
        }
    }

    private void drop(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        try {
            session.close(status);
        } catch (IOException | RuntimeException alreadyGone) {
            log.debug("Collab socket was already closed: {}", alreadyGone.toString());
        }
    }
}
