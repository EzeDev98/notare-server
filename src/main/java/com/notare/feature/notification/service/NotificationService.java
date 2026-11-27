package com.notare.feature.notification.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.dto.response.NotificationResponse;
import com.notare.feature.notification.dto.response.UnreadCountResponse;
import org.springframework.data.domain.Pageable;

/** Reads, marks and creates a user's notifications. */
public interface NotificationService {

    void create(CreateNotificationCommand command);

    PagedResponse<NotificationResponse> list(Long recipientId, boolean unreadOnly, Pageable pageable);

    UnreadCountResponse unreadCount(Long recipientId);

    NotificationResponse markAsRead(Long recipientId, Long notificationId);

    int markAllAsRead(Long recipientId);

    void delete(Long recipientId, Long notificationId);
}
