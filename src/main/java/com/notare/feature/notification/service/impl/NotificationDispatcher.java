package com.notare.feature.notification.service.impl;

import com.notare.feature.notification.channel.NotificationChannel;
import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fans one notification out to every configured channel.
 *
 * <p>Spring injects every NotificationChannel bean into the constructor list,
 * so adding email or push means adding a @Component and nothing else — no
 * registration, no edit here, no change to any listener.
 *
 * <p><b>Each channel is isolated in its own try/catch.</b> That's the point of
 * this class. Without it, an email provider timing out would abort the loop and
 * the user would silently lose their in-app notification too — one channel's
 * outage degrading a completely unrelated one. Failures are logged and
 * swallowed, because there is no caller who can do anything useful with the
 * exception: the listeners upstream are fire-and-forget, and the operation that
 * triggered all this has already committed.
 *
 * <p>Swallowing exceptions is normally a smell. It's correct here for a narrow
 * reason worth being explicit about — notifications are best-effort by design,
 * and the alternative is letting a notification failure damage something that
 * matters more. The log line is what makes it defensible: a swallowed error
 * that leaves no trace is a different thing entirely.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;

    public void dispatch(CreateNotificationCommand command) {
        for (NotificationChannel channel : channels) {
            try {
                channel.deliver(command);
            } catch (Exception ex) {
                log.warn("Notification channel '{}' failed for recipient {} ({})",
                        channel.name(), command.recipientId(), command.type(), ex);
            }
        }
    }
}