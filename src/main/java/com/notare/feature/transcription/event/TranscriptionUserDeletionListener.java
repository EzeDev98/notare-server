package com.notare.feature.transcription.event;

import com.notare.feature.transcription.repository.TranscriptionRepository;
import com.notare.feature.user.event.UserDeletionRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The transcription module's part of an account deletion (step 17g): the transcripts of this user's
 * own clips go, and the name goes from transcripts they only asked for on someone else's clip.
 */
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
