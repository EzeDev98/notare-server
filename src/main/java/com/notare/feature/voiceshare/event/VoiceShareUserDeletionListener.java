package com.notare.feature.voiceshare.event;

import com.notare.feature.user.event.UserDeletionRequested;
import com.notare.feature.voiceshare.repository.VoiceShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** The voice share module's part of an account deletion (step 17g): every voice grant this user gave or received. */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceShareUserDeletionListener {

    private final VoiceShareRepository voiceShares;

    @EventListener
    public void onUserDeletionRequested(UserDeletionRequested event) {
        int removed = voiceShares.deleteAllInvolving(event.userId());
        log.debug("Removed {} voice share(s) of account {}", removed, event.userId());
    }
}
