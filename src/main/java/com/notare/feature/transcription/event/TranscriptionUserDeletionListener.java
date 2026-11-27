package com.notare.feature.transcription.event;

import com.notare.feature.transcription.repository.TranscriptionRepository;
import com.notare.feature.user.event.UserDeletionRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Deletes or anonymises a user's transcriptions when their account is deleted. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranscriptionUserDeletionListener {

    private final TranscriptionRepository transcriptions;

    @EventListener
    public void onUserDeletionRequested(UserDeletionRequested event) {
        int removed = transcriptions.deleteAllOwnedBy(event.userId());
        transcriptions.clearRequestedBy(event.userId());
        log.debug("Removed {} transcript(s) of account {}", removed, event.userId());
    }
}
