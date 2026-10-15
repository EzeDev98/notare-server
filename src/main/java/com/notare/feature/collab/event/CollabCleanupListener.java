package com.notare.feature.collab.event;

import com.notare.feature.collab.repository.NoteSnapshotRepository;
import com.notare.feature.collab.repository.NoteUpdateRepository;
import com.notare.feature.note.event.NotePermanentlyDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Removes a note's collaboration data when the note is deleted for good (step 15e).
 *
 * <p>BEFORE_COMMIT, for the reason NoteShareCleanupListener gives: this deletes
 * rows, so it must commit or roll back together with the delete that caused it.
 * note_updates and note_snapshots hold ids, not foreign keys, so nothing else
 * would ever remove them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollabCleanupListener {

    private final NoteUpdateRepository updates;
    private final NoteSnapshotRepository snapshots;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onNotePermanentlyDeleted(NotePermanentlyDeletedEvent event) {
        updates.deleteAllForNote(event.noteId());
        if (snapshots.existsById(event.noteId())) {
            snapshots.deleteById(event.noteId());
        }
        log.debug("Removed collaboration data for permanently deleted note {}", event.noteId());
    }
}
