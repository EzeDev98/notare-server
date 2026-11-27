package com.notare.feature.notification.event;

import com.notare.feature.notification.repository.NotificationRepository;
import com.notare.feature.user.event.UserDeletionSweep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Deletes a user's notifications when their account is deleted. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationUserDeletionListener {

    private final NotificationRepository notifications;

    @EventListener
    public void onUserDeletionSweep(UserDeletionSweep event) {
        int removed = notifications.deleteAllForRecipient(event.userId());
        notifications.clearActor(event.userId());
        log.debug("Removed {} notification(s) of account {}", removed, event.userId());
    }
}
