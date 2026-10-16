package com.notare.feature.note.event;

import com.notare.feature.note.repository.NoteRepository;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.user.event.UserDeletionRequested;
import com.notare.feature.user.event.UserDeletionSweep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The note module's part of an account deletion (step 17g, B-03).
 *
 * <p>Notes go through the normal permanent delete, one by one, so NotePermanentlyDeletedEvent runs
 * every cascade exactly as for a manual delete: shares, collaboration data, clips and their stored
 * files, and the sync tombstone. Plain {@code @EventListener}, so those cascades register properly
 * inside the deletion's transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoteUserDeletionListener {

    private final NoteRepository notes;
    private final NoteService noteService;

    @EventListener
    public void onUserDeletionRequested(UserDeletionRequested event) {
        List<Long> noteIds = notes.findAllIdsByOwnerId(event.userId());
        for (Long noteId : noteIds) {
            noteService.permanentlyDeleteNote(event.userId(), noteId);
        }
        log.debug("Deleted {} note(s) of account {}", noteIds.size(), event.userId());
    }

    @EventListener
    public void onUserDeletionSweep(UserDeletionSweep event) {
        notes.clearLastModifiedBy(event.userId());
    }
}
