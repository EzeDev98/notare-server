package com.notare.feature.notification.channel;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The in-app channel: persist the notification so it shows up in the user's
 * list and unread count.
 *
 * <p>Treating persistence as just another channel — rather than as the "real"
 * behaviour with delivery bolted on — is what keeps the seam honest. An email
 * channel added later is a peer of this class, not a special case handled
 * inside it.
 */
@Component
@RequiredArgsConstructor
public class InAppNotificationChannel implements NotificationChannel {

    private final NotificationService notificationService;

    @Override
    public String name() {
        return "in-app";
    }

    @Override
    public void deliver(CreateNotificationCommand command) {
        // create() runs in its own REQUIRES_NEW transaction, so this write
        // commits or fails on its own without touching anything upstream.
        notificationService.create(command);
    }
}