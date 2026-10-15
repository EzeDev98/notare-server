package com.notare.feature.collab.event;

import com.notare.feature.collab.enums.CollabRole;
import com.notare.feature.collab.socket.CollabAttributes;
import com.notare.feature.collab.socket.CollabProtocol;
import com.notare.feature.collab.socket.CollabSessions;
import com.notare.feature.collaboration.event.CollaborationRemovedEvent;
import com.notare.feature.note.event.NotePermanentlyDeletedEvent;
import com.notare.feature.note.event.NoteSoftDeletedEvent;
import com.notare.feature.note.repository.NoteRepository;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.event.NoteSharePermissionChangedEvent;
import com.notare.feature.share.event.NoteShareRevokedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Objects;

/**
 * Cuts or changes live connections when the access behind them changes (step 15e).
 *
 * <h2>Why AFTER_COMMIT, unlike the share cleanup listeners</h2>
 *
 * The cleanup listeners use BEFORE_COMMIT because they delete rows, and a delete
 * that fails must roll the cause back with it. This listener touches no rows: it
 * only closes sockets and sends frames. If it ran before commit and the
 * transaction then rolled back, a person would be thrown out of a note whose
 * access was never actually removed. After commit, a cut always means the change
 * is real and durable.
 *
 * <p>It runs on the committing thread, not async, so the socket is closed before
 * the owner's request returns. If this listener fails the access change still
 * stands, and the person's next ticket request is refused, so the failure costs
 * at most the rest of one open session.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollabAccessListener {

    private final CollabSessions sessions;
    private final CollabProtocol protocol;
    private final NoteRepository noteRepository;

    /** One grant withdrawn by hand: close that person's sockets on that note. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShareRevoked(NoteShareRevokedEvent event) {
        closeUserOnNote(event.sharedWithUserId(), event.noteId());
    }

    /**
     * A grant moved between READ and WRITE: change the role on that person's open
     * sockets and tell them with a ROLE frame, so the editor can lock or unlock
     * without reconnecting. The owner's own sessions are never touched.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPermissionChanged(NoteSharePermissionChangedEvent event) {
        CollabRole role = event.newPermission().satisfies(SharePermission.WRITE)
                ? CollabRole.EDITOR
                : CollabRole.VIEWER;

        for (WebSocketSession session : sessions.forNote(event.noteId())) {
            if (!Objects.equals(CollabAttributes.userId(session), event.sharedWithUserId())) {
                continue;
            }
            CollabRole current = CollabAttributes.role(session);
            if (current == CollabRole.OWNER || current == role) {
                continue;
            }
            CollabAttributes.setRole(session, role);
            protocol.sendRole(session, role);
        }
    }

    /** Into the trash: nobody can see the note any more, the owner included. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSoftDeleted(NoteSoftDeletedEvent event) {
        closeAllOnNote(event.noteId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPermanentlyDeleted(NotePermanentlyDeletedEvent event) {
        closeAllOnNote(event.noteId());
    }

    /**
     * An owner ended a connection. The share module deletes every grant between the
     * two, without a revoke event per note, so close any socket that person has on a
     * note this owner owns. The owner id is read from the note, never from the socket.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCollaborationRemoved(CollaborationRemovedEvent event) {
        for (WebSocketSession session : sessions.all()) {
            if (!Objects.equals(CollabAttributes.userId(session), event.collaboratorId())) {
                continue;
            }
            boolean ownedByRemover = noteRepository.findById(CollabAttributes.noteId(session))
                    .map(note -> Objects.equals(note.getOwnerId(), event.ownerId()))
                    .orElse(false);
            if (ownedByRemover) {
                close(session);
            }
        }
    }

    private void closeUserOnNote(Long userId, Long noteId) {
        for (WebSocketSession session : sessions.forNote(noteId)) {
            if (Objects.equals(CollabAttributes.userId(session), userId)) {
                close(session);
            }
        }
    }

    private void closeAllOnNote(Long noteId) {
        sessions.forNote(noteId).forEach(this::close);
    }

    private void close(WebSocketSession session) {
        sessions.remove(session.getId());
        try {
            session.close(CollabProtocol.CLOSE_FORBIDDEN);
        } catch (IOException | RuntimeException alreadyGone) {
            log.debug("Collab socket was already closed: {}", alreadyGone.toString());
        }
    }
}
