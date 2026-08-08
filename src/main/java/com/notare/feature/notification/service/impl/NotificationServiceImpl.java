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

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    /**
     * REQUIRES_NEW, deliberately.
     *
     * <p>The callers are AFTER_COMMIT listeners, which run after their source
     * transaction has finished — so most of the time there's no transaction here
     * and REQUIRED would simply start one, same result. The reason to be
     * explicit is the case where there IS an ambient transaction: a listener
     * added later with a different phase, or a caller inside a request. With
     * REQUIRED, a failure to write a notification would mark that outer
     * transaction rollback-only and take the real operation down with it.
     *
     * <p>That inverts the priority the whole design rests on. An invitation that
     * succeeds without its notification is a minor annoyance; an invitation
     * rolled back because a notification insert failed is a broken feature.
     * REQUIRES_NEW isolates this write so it can fail alone — the exact opposite
     * of the 7F cleanup listener, which was made to fail TOGETHER with its cause
     * because it revoked access.
     */
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
            // Already read. Return as-is rather than restamping: readAt means
            // "when you first read this", and overwriting it on every glance
            // would quietly destroy that meaning. Idempotent by doing nothing.
            return notificationMapper.toResponse(notification);
        }

        notification.setReadAt(LocalDateTime.now());
        return notificationMapper.toResponse(notificationRepository.saveAndFlush(notification));
    }

    /**
     * The timestamp is generated HERE and passed into the query, not left to
     * the database or to auditing. The bulk UPDATE skips entity callbacks, so
     * @LastModifiedDate-style population never happens — and generating it in
     * one place means every row in a single "mark all" carries the identical
     * instant, which is what actually happened.
     */
    @Override
    @Transactional
    public int markAllAsRead(Long recipientId) {
        return notificationRepository.markAllAsRead(recipientId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void delete(Long recipientId, Long notificationId) {
        // Fetch recipient-scoped, then delete the managed entity — never
        // deleteById, which would drop the ownership predicate.
        notificationRepository.delete(requireOwned(recipientId, notificationId));
    }

    /**
     * Loads a notification scoped to its recipient. Someone else's id returns
     * empty and surfaces as 404, so the API never confirms it exists.
     */
    private Notification requireOwned(Long recipientId, Long notificationId) {
        return notificationRepository
                .findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found."));
    }
}