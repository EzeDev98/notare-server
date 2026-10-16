package com.notare.feature.share.event;

import com.notare.feature.share.repository.NoteShareRepository;
import com.notare.feature.user.event.UserDeletionRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** The share module's part of an account deletion (step 17g): every grant this user gave or received. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareUserDeletionListener {

    private final NoteShareRepository noteShares;

    @EventListener
    public void onUserDeletionRequested(UserDeletionRequested event) {
        int removed = noteShares.deleteAllInvolving(event.userId());
        log.debug("Removed {} note share(s) of account {}", removed, event.userId());
    }
}
