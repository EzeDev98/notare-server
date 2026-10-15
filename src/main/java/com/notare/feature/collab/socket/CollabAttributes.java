package com.notare.feature.collab.socket;

import com.notare.feature.collab.enums.CollabRole;
import org.springframework.web.socket.WebSocketSession;

/**
 * The keys the handshake attaches to a session, and small readers for them.
 * Everything the socket code needs to know about a connection is here, so no
 * other class has to guess an attribute name.
 */
public final class CollabAttributes {

    public static final String USER_ID = "collab.userId";
    public static final String NOTE_ID = "collab.noteId";
    public static final String ROLE = "collab.role";
    public static final String LAST_SEEN = "collab.lastSeen";
    public static final String JOINED = "collab.joined";
    public static final String WINDOW_START = "collab.windowStart";
    public static final String WINDOW_COUNT = "collab.windowCount";

    private CollabAttributes() {
    }

    public static Long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get(USER_ID);
    }

    public static Long noteId(WebSocketSession session) {
        return (Long) session.getAttributes().get(NOTE_ID);
    }

    public static CollabRole role(WebSocketSession session) {
        return (CollabRole) session.getAttributes().get(ROLE);
    }

    /** Anything heard from the client (a frame or a pong) proves it is still there. */
    public static void touch(WebSocketSession session) {
        session.getAttributes().put(LAST_SEEN, System.currentTimeMillis());
    }

    public static long lastSeen(WebSocketSession session) {
        Object value = session.getAttributes().get(LAST_SEEN);
        return value instanceof Long millis ? millis : 0L;
    }

    /** True once JOIN has been answered with SYNCED. Only joined sessions receive broadcasts. */
    public static boolean joined(WebSocketSession session) {
        return Boolean.TRUE.equals(session.getAttributes().get(JOINED));
    }

    public static void markJoined(WebSocketSession session) {
        session.getAttributes().put(JOINED, Boolean.TRUE);
    }

    /** Used when a share changes between READ and WRITE while the socket is open (15e). */
    public static void setRole(WebSocketSession session, CollabRole role) {
        session.getAttributes().put(ROLE, role);
    }
}
