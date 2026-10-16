package com.notare.feature.collaboration.event;

import com.notare.feature.collaboration.repository.CollaborationRepository;
import com.notare.feature.user.event.UserDeletionRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** The collaboration module's part of an account deletion (step 17g): every connection this user is part of. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollaborationUserDeletionListener {

    private final CollaborationRepository collaborations;

    @EventListener
    public void onUserDeletionRequested(UserDeletionRequested event) {
        int removed = collaborations.deleteAllInvolving(event.userId());
        log.debug("Removed {} connection(s) of account {}", removed, event.userId());
    }
}
