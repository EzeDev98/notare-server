package com.notare.feature.notification.channel;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Delivers a notification by saving it to the user's in-app list. */
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
        notificationService.create(command);
    }
}
