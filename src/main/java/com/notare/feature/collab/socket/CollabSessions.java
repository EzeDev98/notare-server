package com.notare.feature.collab.socket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every open collaboration socket, in memory (step 15c, D-032: one server
 * instance for now).
 *
 * <p>Sessions are stored wrapped in ConcurrentWebSocketSessionDecorator, because
 * a WebSocketSession must not be written to from two threads at once and a
 * broadcast (15d) and the heartbeat both write. The wrapper also bounds how long
 * a slow client can hold a sender up, so one stuck tab cannot stall the rest.
 */
@Component
public class CollabSessions {

    /** A send that takes longer than this closes the session. */
    private static final int SEND_TIME_LIMIT_MS = 10_000;

    /** Bytes that may queue for one slow client before it is closed. */
    private static final int SEND_BUFFER_LIMIT_BYTES = 1_048_576;

    private final Map<String, ConcurrentWebSocketSessionDecorator> byId = new ConcurrentHashMap<>();

    public ConcurrentWebSocketSessionDecorator register(WebSocketSession session) {
        ConcurrentWebSocketSessionDecorator wrapped =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        byId.put(session.getId(), wrapped);
        return wrapped;
    }

    public void remove(String sessionId) {
        byId.remove(sessionId);
    }

    public Optional<ConcurrentWebSocketSessionDecorator> find(String sessionId) {
        return Optional.ofNullable(byId.get(sessionId));
    }

    public Collection<ConcurrentWebSocketSessionDecorator> all() {
        return List.copyOf(byId.values());
    }

    /** The open sessions on one note, for broadcasting (15d) and cutting access (15e). */
    public List<ConcurrentWebSocketSessionDecorator> forNote(Long noteId) {
        return byId.values().stream()
                .filter(session -> Objects.equals(CollabAttributes.noteId(session), noteId))
                .toList();
    }
}
