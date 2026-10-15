package com.notare.feature.collab.socket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

/**
 * The collaboration socket (step 15c). Frames are binary only; BinaryWebSocketHandler
 * already closes a session that sends text.
 *
 * <p>This step owns the connection: who is connected, and whether they are
 * still there. What the frames mean (JOIN, UPDATE, SNAPSHOT, AWARENESS, ROLE)
 * is the protocol and arrives in 15d, in CollabProtocol, which handleBinaryMessage
 * will hand each frame to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollabWebSocketHandler extends BinaryWebSocketHandler {

    private final CollabSessions sessions;
    private final CollabProtocol protocol;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.register(session);
        CollabAttributes.touch(session);
        log.debug("Collab socket opened: note={} user={} role={}",
                CollabAttributes.noteId(session),
                CollabAttributes.userId(session),
                CollabAttributes.role(session));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        CollabAttributes.touch(session);
        protocol.handle(session, message.getPayload());
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        CollabAttributes.touch(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Collab socket transport error: {}", exception.toString());
        sessions.remove(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }
}
