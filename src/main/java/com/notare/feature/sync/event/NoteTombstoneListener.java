package com.notare.feature.sync.event;

import com.notare.feature.note.event.NotePermanentlyDeletedEvent;
import com.notare.feature.sync.model.NoteTombstone;
import com.notare.feature.sync.repository.NoteTombstoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * Records a tombstone when a note is permanently deleted, so offline clients
 * eventually learn it's gone.
 *
 * <h2>BEFORE_COMMIT, and the reasoning is the same shape as 7F</h2>
 *
 * The phase question has come up four times now and the answer has differed
 * each time for a stated reason. Here: if this ran AFTER_COMMIT and failed, the
 * note would be destroyed while no tombstone existed — and unlike a missed
 * notification, there is no second chance. The evidence that the note ever
 * existed is gone, so nothing can reconstruct the fact later. Every offline
 * client keeps that note in its cache indefinitely, and no error anywhere says
 * why.
 *
 * <p>BEFORE_COMMIT puts the tombstone in the same transaction as the deletion:
 * both happen or neither does. A failure means the delete fails and the user
 * retries, which is recoverable. A silent permanent divergence between devices
 * is not.
 *
 * <p>The general rule this keeps landing on: run in the transaction when the
 * side-effect is the only record of something irreversible, and after it when a
 * failure merely costs a convenience.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoteTombstoneListener {

    private final NoteTombstoneRepository noteTombstoneRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onNotePermanentlyDeleted(NotePermanentlyDeletedEvent event) {
        NoteTombstone tombstone = new NoteTombstone();
        tombstone.setNoteId(event.noteId());
        tombstone.setOwnerId(event.ownerId());

        // Set explicitly rather than relying on auditing: this timestamp is
        // compared against client cursors, so it must be the moment of deletion
        // and not whatever a listener ordering happens to produce.
        tombstone.setDeletedAt(LocalDateTime.now());

        noteTombstoneRepository.save(tombstone);
        log.debug("Recorded tombstone for permanently deleted note {}", event.noteId());
    }
}