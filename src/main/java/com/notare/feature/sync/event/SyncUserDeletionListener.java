package com.notare.feature.sync.event;

import com.notare.feature.sync.repository.NoteTombstoneRepository;
import com.notare.feature.sync.repository.SyncOperationRepository;
import com.notare.feature.user.event.UserDeletionSweep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Deletes a user's sync records when their account is deleted. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncUserDeletionListener {

    private final NoteTombstoneRepository tombstones;
    private final SyncOperationRepository operations;

    @EventListener
    public void onUserDeletionSweep(UserDeletionSweep event) {
        int removed = tombstones.deleteAllOwnedBy(event.userId());
        operations.deleteAllOwnedBy(event.userId());
        log.debug("Removed {} tombstone(s) of account {}", removed, event.userId());
    }
}
