package com.notare.feature.notification.channel;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;

/** One way of delivering a notification. */
public interface NotificationChannel {

    String name();

    void deliver(CreateNotificationCommand command);
}
