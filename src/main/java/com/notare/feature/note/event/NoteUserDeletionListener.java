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

/** Deletes a user's notes when their account is deleted. */
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
