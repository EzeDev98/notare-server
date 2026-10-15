package com.notare.feature.collab.socket;

import com.notare.feature.collab.enums.CollabRole;
import com.notare.feature.collab.model.NoteSnapshot;
import com.notare.feature.collab.model.NoteUpdate;
import com.notare.feature.collab.service.CollabStoreService;
import com.notare.feature.collab.service.CollabStoreService.SnapshotResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

/**
 * The whole collaboration protocol, in one class (step 15d, D-032). The wire
 * format is written up in docs/collab-protocol.md and the step 16 client mirrors
 * that document exactly.
 *
 * <p>Every frame is binary and its first byte is the type. Numbers are big-endian.
 * The server never reads Yjs bytes: it numbers, stores and forwards them.
 *
 * <p>One lock per note (striped) orders everything that changes or reads the
 * note's log: an UPDATE, a SNAPSHOT, and the replay a JOIN does. That is what
 * keeps a joiner from missing an update, or seeing one twice, and keeps seqs
 * broadcast in the order they were assigned. It holds for one server instance,
 * which is the D-032 assumption.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollabProtocol {

    // Frame types.
    public static final byte JOIN = 1;
    public static final byte UPDATE = 2;
    public static final byte SNAPSHOT = 3;
    public static final byte AWARENESS = 4;
    public static final byte ROLE = 5;
    public static final byte ACK = 6;
    public static final byte SYNCED = 7;
    public static final byte ERROR = 8;

    // Codes carried by an ERROR frame. The socket stays open after one.
    public static final byte ERR_READ_ONLY = 1;
    public static final byte ERR_NOT_JOINED = 2;
    public static final byte ERR_ALREADY_JOINED = 3;
    public static final byte ERR_BAD_SNAPSHOT = 4;
    public static final byte ERR_SERVER = 5;

    // Close codes. 1009 (too big) is the standard one; the 44xx range is ours.
    public static final CloseStatus CLOSE_BAD_FRAME = new CloseStatus(4400, "Bad frame");
    public static final CloseStatus CLOSE_FORBIDDEN = new CloseStatus(4403, "Access removed");
    public static final CloseStatus CLOSE_RATE_LIMIT = new CloseStatus(4429, "Too many frames");

    /** Largest frame accepted, type byte included. */
    public static final int MAX_FRAME_BYTES = 1_048_576;

    /** Frames per second one socket may send; the next one closes it with 4429. */
    public static final int MAX_FRAMES_PER_SECOND = 60;

    private static final int LOCK_STRIPES = 64;

    private final CollabSessions sessions;
    private final CollabStoreService store;

    private final ReentrantLock[] noteLocks = IntStream.range(0, LOCK_STRIPES)
            .mapToObj(i -> new ReentrantLock())
            .toArray(ReentrantLock[]::new);

    // ---- Incoming ---------------------------------------------------------

    public void handle(WebSocketSession raw, ByteBuffer message) {
        // Writes go through the registered decorator so two threads never write at once.
        WebSocketSession session = sessions.find(raw.getId()).<WebSocketSession>map(s -> s).orElse(raw);

        int size = message.remaining();
        if (size > MAX_FRAME_BYTES) {
            close(session, CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }
        if (size == 0) {
            close(session, CLOSE_BAD_FRAME);
            return;
        }
        if (overRate(session)) {
            close(session, CLOSE_RATE_LIMIT);
            return;
        }

        byte[] frame = new byte[size];
        message.get(frame);
        ByteBuffer body = ByteBuffer.wrap(frame, 1, size - 1).slice();

        try {
            switch (frame[0]) {
                case JOIN -> join(session, body);
                case UPDATE -> update(session, body);
                case SNAPSHOT -> snapshot(session, body);
                case AWARENESS -> awareness(session, frame);
                default -> close(session, CLOSE_BAD_FRAME);
            }
        } catch (BufferUnderflowException malformed) {
            close(session, CLOSE_BAD_FRAME);
        } catch (RuntimeException failed) {
            log.warn("Collab frame failed: note={} type={}", CollabAttributes.noteId(session), frame[0], failed);
            send(session, error(ERR_SERVER));
        }
    }

    /** JOIN body: lastSeq (8 bytes), the last seq this device holds, 0 for none. */
    private void join(WebSocketSession session, ByteBuffer body) {
        if (body.remaining() != 8) {
            close(session, CLOSE_BAD_FRAME);
            return;
        }
        if (CollabAttributes.joined(session)) {
            send(session, error(ERR_ALREADY_JOINED));
            return;
        }

        long lastSeq = Math.max(0L, body.getLong());
        Long noteId = CollabAttributes.noteId(session);

        ReentrantLock lock = lockFor(noteId);
        lock.lock();
        try {
            long from = lastSeq;

            NoteSnapshot snapshot = store.snapshot(noteId).orElse(null);
            if (snapshot != null && lastSeq < snapshot.getUpToSeq()) {
                send(session, snapshotFrame(snapshot.getUpToSeq(), snapshot.getState()));
                from = snapshot.getUpToSeq();
            }

            for (NoteUpdate stored : store.updatesAfter(noteId, from)) {
                send(session, updateFrame(stored.getSeq(), stored.getPayload()));
            }

            send(session, seqFrame(SYNCED, store.head(noteId)));
            CollabAttributes.markJoined(session);
        } finally {
            lock.unlock();
        }
    }

    /** UPDATE body: the Yjs update bytes, nothing else. */
    private void update(WebSocketSession session, ByteBuffer body) {
        if (!mayWrite(session)) {
            return;
        }
        if (!body.hasRemaining()) {
            close(session, CLOSE_BAD_FRAME);
            return;
        }

        byte[] payload = new byte[body.remaining()];
        body.get(payload);
        Long noteId = CollabAttributes.noteId(session);

        ReentrantLock lock = lockFor(noteId);
        lock.lock();
        try {
            long seq = store.appendUpdate(noteId, CollabAttributes.userId(session), payload);
            send(session, seqFrame(ACK, seq));
            broadcast(noteId, session, updateFrame(seq, payload));
        } finally {
            lock.unlock();
        }
    }

    /**
     * SNAPSHOT body: upToSeq (8), stateLength (4), state, then the plain text as
     * UTF-8 up to the end of the frame. No reply on success.
     */
    private void snapshot(WebSocketSession session, ByteBuffer body) {
        if (!mayWrite(session)) {
            return;
        }

        long upToSeq = body.getLong();
        int stateLength = body.getInt();
        if (upToSeq < 1 || stateLength <= 0 || stateLength > body.remaining()) {
            send(session, error(ERR_BAD_SNAPSHOT));
            return;
        }

        byte[] state = new byte[stateLength];
        body.get(state);
        byte[] text = new byte[body.remaining()];
        body.get(text);
        String plainText = new String(text, StandardCharsets.UTF_8);

        Long noteId = CollabAttributes.noteId(session);
        ReentrantLock lock = lockFor(noteId);
        lock.lock();
        try {
            if (store.saveSnapshot(noteId, upToSeq, state, plainText) == SnapshotResult.AHEAD) {
                send(session, error(ERR_BAD_SNAPSHOT));
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * AWARENESS body: opaque presence bytes. Relayed as received to the other joined
     * sessions of the note and never stored. Viewers may send it. What a Viewer's
     * presence shows (an avatar, never a named cursor, O-08) is the client's rule,
     * because the server cannot read the bytes.
     */
    private void awareness(WebSocketSession session, byte[] frame) {
        if (!CollabAttributes.joined(session)) {
            send(session, error(ERR_NOT_JOINED));
            return;
        }
        broadcast(CollabAttributes.noteId(session), session, frame);
    }

    /** Joined, and allowed to write. Otherwise the caller gets an ERROR and nothing is stored. */
    private boolean mayWrite(WebSocketSession session) {
        if (!CollabAttributes.joined(session)) {
            send(session, error(ERR_NOT_JOINED));
            return false;
        }
        // Read fresh each time: a role change while connected (15e) takes effect on the next frame.
        if (!CollabAttributes.role(session).canWrite()) {
            send(session, error(ERR_READ_ONLY));
            return false;
        }
        return true;
    }

    // ---- Outgoing ---------------------------------------------------------

    /** Used by 15e when a role changes while the socket is open. */
    public void sendRole(WebSocketSession session, CollabRole role) {
        send(session, new byte[]{ROLE, roleByte(role)});
    }

    /** Wire value of a role: 0 owner, 1 editor, 2 viewer. */
    static byte roleByte(CollabRole role) {
        return switch (role) {
            case OWNER -> 0;
            case EDITOR -> 1;
            case VIEWER -> 2;
        };
    }

    /** UPDATE from the server: seq (8) then the update bytes. */
    private static byte[] updateFrame(long seq, byte[] payload) {
        return ByteBuffer.allocate(9 + payload.length).put(UPDATE).putLong(seq).put(payload).array();
    }

    /** SNAPSHOT from the server: upToSeq (8) then the Yjs state. */
    private static byte[] snapshotFrame(long upToSeq, byte[] state) {
        return ByteBuffer.allocate(9 + state.length).put(SNAPSHOT).putLong(upToSeq).put(state).array();
    }

    /** ACK and SYNCED: one seq (8). */
    private static byte[] seqFrame(byte type, long seq) {
        return ByteBuffer.allocate(9).put(type).putLong(seq).array();
    }

    private static byte[] error(byte code) {
        return new byte[]{ERROR, code};
    }

    private void broadcast(Long noteId, WebSocketSession origin, byte[] frame) {
        for (WebSocketSession other : sessions.forNote(noteId)) {
            if (!other.getId().equals(origin.getId()) && other.isOpen() && CollabAttributes.joined(other)) {
                send(other, frame);
            }
        }
    }

    private void send(WebSocketSession session, byte[] frame) {
        try {
            session.sendMessage(new BinaryMessage(frame));
        } catch (IOException | RuntimeException failed) {
            // A slow or gone client. The decorator closes it past its limits; the heartbeat clears the rest.
            log.debug("Collab send failed: {}", failed.toString());
        }
    }

    private void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException | RuntimeException alreadyGone) {
            log.debug("Collab socket was already closed: {}", alreadyGone.toString());
        }
    }

    // ---- Helpers ----------------------------------------------------------

    private ReentrantLock lockFor(Long noteId) {
        return noteLocks[Math.floorMod(Long.hashCode(noteId), LOCK_STRIPES)];
    }

    /** A fixed one-second window per socket. The frame that tips it over closes the socket. */
    private boolean overRate(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        long now = System.currentTimeMillis();
        long start = attributes.get(CollabAttributes.WINDOW_START) instanceof Long l ? l : 0L;

        if (now - start >= 1_000) {
            attributes.put(CollabAttributes.WINDOW_START, now);
            attributes.put(CollabAttributes.WINDOW_COUNT, 1);
            return false;
        }

        int count = (attributes.get(CollabAttributes.WINDOW_COUNT) instanceof Integer i ? i : 0) + 1;
        attributes.put(CollabAttributes.WINDOW_COUNT, count);
        return count > MAX_FRAMES_PER_SECOND;
    }
}
