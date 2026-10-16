package com.notare.feature.collab.event;

import com.notare.feature.collab.repository.NoteUpdateRepository;
import com.notare.feature.user.event.UserDeletionSweep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The collab module's part of an account deletion (step 17g). A deleted note already took its updates
 * with it. What is left are this person's edits to OTHER people's notes: they stay with the note,
 * credited to its owner, because a collaborative text cannot be un-merged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollabUserDeletionListener {

    private final NoteUpdateRepository updates;

    @EventListener
    public void onUserDeletionSweep(UserDeletionSweep event) {
        int reassigned = updates.reassignAuthorsToNoteOwners(event.userId());
        updates.deleteAuthoredBy(event.userId());
        log.debug("Re-credited {} update(s) of account {} to the note owners", reassigned, event.userId());
    }
}
