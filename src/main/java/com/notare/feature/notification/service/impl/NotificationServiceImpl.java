package com.notare.feature.notification.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.NotificationNotFoundException;
import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.dto.response.NotificationResponse;
import com.notare.feature.notification.dto.response.UnreadCountResponse;
import com.notare.feature.notification.mapper.NotificationMapper;
import com.notare.feature.notification.model.Notification;
import com.notare.feature.notification.repository.NotificationRepository;
import com.notare.feature.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Reads, marks and creates a user's notifications. */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(CreateNotificationCommand command) {
        notificationRepository.save(notificationMapper.toEntity(command));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> list(Long recipientId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadAtIsNull(recipientId, pageable)
                : notificationRepository.findByRecipientId(recipientId, pageable);

        List<NotificationResponse> content = page.getContent().stream()
                .map(notificationMapper::toResponse)
                .toList();
        return PagedResponse.of(content, page);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long recipientId) {
        return new UnreadCountResponse(
                notificationRepository.countByRecipientIdAndReadAtIsNull(recipientId));
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long recipientId, Long notificationId) {
        Notification notification = requireOwned(recipientId, notificationId);

        if (notification.getReadAt() != null) {
            return notificationMapper.toResponse(notification);
        }

        notification.setReadAt(LocalDateTime.now());
        return notificationMapper.toResponse(notificationRepository.saveAndFlush(notification));
    }

    @Override
    @Transactional
    public int markAllAsRead(Long recipientId) {
        return notificationRepository.markAllAsRead(recipientId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void delete(Long recipientId, Long notificationId) {
        notificationRepository.delete(requireOwned(recipientId, notificationId));
    }

    private Notification requireOwned(Long recipientId, Long notificationId) {
        return notificationRepository
                .findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found."));
    }
}
