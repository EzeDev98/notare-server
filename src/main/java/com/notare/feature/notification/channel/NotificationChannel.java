package com.notare.feature.notification.channel;

import com.notare.feature.notification.dto.command.CreateNotificationCommand;

/**
 * One way of getting a notification to a person. Strategy pattern, same shape
 * as TranscriptionEngine and FileStorageService.
 *
 * <p>The seam exists now, with exactly one implementation, because retrofitting
 * it later is the expensive version: by then the listeners would be calling
 * NotificationService.create directly and every one of them would need
 * rewriting to add email or push. Adding a channel today means adding a class.
 *
 * <p>Deliberately takes the COMMAND rather than the saved entity. An email or
 * push channel has no interest in a database row — it needs the facts, and the
 * facts are what the command carries. Passing the entity would also imply an
 * ordering ("persist first, then deliver") that isn't inherent: a future
 * push-only channel has nothing to persist.
 *
 * <p>Implementations must not throw for ordinary failures — see the dispatcher
 * for why. Delivery is best-effort by design.
 */
public interface NotificationChannel {

    /** A short name for logging, so a failure says WHICH channel failed. */
    String name();

    /** Deliver, or record, one notification. */
    void deliver(CreateNotificationCommand command);
}