package com.notare.feature.notification.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.dto.response.NotificationResponse;
import com.notare.feature.notification.dto.response.UnreadCountResponse;
import org.springframework.data.domain.Pageable;

/**
 * Reading and managing a user's notifications — plus the one write that isn't
 * user-driven.
 *
 * <p>Note the asymmetry: everything except {@link #create} is scoped to the
 * acting user and reachable from a controller. {@code create} has NO recipient
 * check at all, because the recipient is decided by the event that triggered
 * it, and no endpoint will ever expose it. Notifications are something the
 * system tells you, never something a client can post to another user — an
 * endpoint for that would be a spam vector wearing a feature's clothes.
 */
public interface NotificationService {

    /**
     * Records one notification. Called only by the listeners in 8D.
     *
     * <p>Returns void rather than a response: nobody is waiting on the result,
     * the caller is a fire-and-forget listener, and handing back a DTO would
     * imply otherwise.
     */
    void create(CreateNotificationCommand command);

    /**
     * My notifications, newest first.
     *
     * @param unreadOnly narrows to unread. A flag rather than two methods
     *                   because it maps to one query parameter and the two
     *                   queries differ by a single predicate; splitting it
     *                   would duplicate the paging and mapping either side.
     */
    PagedResponse<NotificationResponse> list(Long recipientId, boolean unreadOnly, Pageable pageable);

    /** How many I haven't read. */
    UnreadCountResponse unreadCount(Long recipientId);

    /** Mark one as read. Idempotent — re-reading doesn't move the timestamp. */
    NotificationResponse markAsRead(Long recipientId, Long notificationId);

    /**
     * Mark everything unread as read.
     *
     * @return how many rows changed, so the client can settle its badge without
     *         a follow-up count request.
     */
    int markAllAsRead(Long recipientId);

    /** Remove one notification from my list. */
    void delete(Long recipientId, Long notificationId);
}