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

/** Records a tombstone when a note is deleted for good. */
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

        tombstone.setDeletedAt(LocalDateTime.now());

        noteTombstoneRepository.save(tombstone);
        log.debug("Recorded tombstone for permanently deleted note {}", event.noteId());
    }
}
