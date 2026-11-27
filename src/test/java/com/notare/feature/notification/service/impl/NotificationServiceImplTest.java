package com.notare.feature.notification.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.NotificationNotFoundException;
import com.notare.feature.notification.dto.command.CreateNotificationCommand;
import com.notare.feature.notification.dto.response.NotificationResponse;
import com.notare.feature.notification.dto.response.UnreadCountResponse;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.mapper.NotificationMapper;
import com.notare.feature.notification.model.Notification;
import com.notare.feature.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests for NotificationServiceImpl. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final Long RECIPIENT_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long NOTIFICATION_ID = 10L;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationMapper notificationMapper;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, notificationMapper);
    }

    private Notification notification(Long recipientId, LocalDateTime readAt) {
        Notification n = new Notification();
        n.setId(NOTIFICATION_ID);
        n.setRecipientId(recipientId);
        n.setType(NotificationType.TRANSCRIPTION_COMPLETED);
        n.setTargetType(NotificationTargetType.NOTE);
        n.setTargetId(42L);
        n.setTargetLabel("Q3 Plan");
        n.setReadAt(readAt);
        return n;
    }

    @Test
    void create_mapsTheCommandThroughTheMapperAndSavesTheResultingEntity() {
        CreateNotificationCommand command = new CreateNotificationCommand(
                RECIPIENT_ID, NotificationType.TRANSCRIPTION_COMPLETED, OTHER_USER_ID,
                NotificationTargetType.NOTE, 42L, "Q3 Plan");
        Notification entity = new Notification();
        when(notificationMapper.toEntity(command)).thenReturn(entity);

        service.create(command);

        verify(notificationRepository).save(entity);
    }

    @Test
    void list_unreadOnlyFalse_usesTheFullListQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Notification n = notification(RECIPIENT_ID, null);
        Page<Notification> page = new PageImpl<>(List.of(n), pageable, 1);
        when(notificationRepository.findByRecipientId(RECIPIENT_ID, pageable)).thenReturn(page);
        NotificationResponse response = mock(NotificationResponse.class);
        when(notificationMapper.toResponse(n)).thenReturn(response);

        PagedResponse<NotificationResponse> result = service.list(RECIPIENT_ID, false, pageable);

        assertThat(result.content()).containsExactly(response);
        verify(notificationRepository, never()).findByRecipientIdAndReadAtIsNull(any(), any());
    }

    @Test
    void list_unreadOnlyTrue_usesTheUnreadOnlyQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        Notification n = notification(RECIPIENT_ID, null);
        Page<Notification> page = new PageImpl<>(List.of(n), pageable, 1);
        when(notificationRepository.findByRecipientIdAndReadAtIsNull(RECIPIENT_ID, pageable)).thenReturn(page);
        NotificationResponse response = mock(NotificationResponse.class);
        when(notificationMapper.toResponse(n)).thenReturn(response);

        PagedResponse<NotificationResponse> result = service.list(RECIPIENT_ID, true, pageable);

        assertThat(result.content()).containsExactly(response);
        verify(notificationRepository, never()).findByRecipientId(anyLong(), any());
    }

    @Test
    void list_mapsEveryPageEntryAndPreservesPagingMetadata() {
        Pageable pageable = PageRequest.of(1, 10);
        Notification first = notification(RECIPIENT_ID, null);
        Notification second = notification(RECIPIENT_ID, null);
        second.setId(11L);
        Page<Notification> page = new PageImpl<>(List.of(first, second), pageable, 22);
        when(notificationRepository.findByRecipientId(RECIPIENT_ID, pageable)).thenReturn(page);
        NotificationResponse firstResponse = mock(NotificationResponse.class);
        NotificationResponse secondResponse = mock(NotificationResponse.class);
        when(notificationMapper.toResponse(first)).thenReturn(firstResponse);
        when(notificationMapper.toResponse(second)).thenReturn(secondResponse);

        PagedResponse<NotificationResponse> result = service.list(RECIPIENT_ID, false, pageable);

        assertThat(result.content()).containsExactly(firstResponse, secondResponse);
        assertThat(result.totalElements()).isEqualTo(22);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isTrue();
    }

    @Test
    void unreadCount_wrapsTheRepositoryCountInTheResponseEnvelope() {
        when(notificationRepository.countByRecipientIdAndReadAtIsNull(RECIPIENT_ID)).thenReturn(7L);

        UnreadCountResponse result = service.unreadCount(RECIPIENT_ID);

        assertThat(result).isEqualTo(new UnreadCountResponse(7L));
    }

    @Test
    void markAsRead_unreadNotification_stampsReadAtAndFlushesImmediately() {
        Notification n = notification(RECIPIENT_ID, null);
        when(notificationRepository.findByIdAndRecipientId(NOTIFICATION_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(n));
        when(notificationRepository.saveAndFlush(n)).thenReturn(n);
        NotificationResponse response = mock(NotificationResponse.class);
        when(notificationMapper.toResponse(n)).thenReturn(response);

        NotificationResponse result = service.markAsRead(RECIPIENT_ID, NOTIFICATION_ID);

        assertThat(result).isEqualTo(response);
        assertThat(n.getReadAt()).isNotNull();
        verify(notificationRepository).saveAndFlush(n);
    }

    @Test
    void markAsRead_alreadyReadNotification_isIdempotent_doesNotRestampOrSave() {
        LocalDateTime firstReadAt = LocalDateTime.of(2026, 1, 1, 9, 0);
        Notification n = notification(RECIPIENT_ID, firstReadAt);
        when(notificationRepository.findByIdAndRecipientId(NOTIFICATION_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(n));
        NotificationResponse response = mock(NotificationResponse.class);
        when(notificationMapper.toResponse(n)).thenReturn(response);

        NotificationResponse result = service.markAsRead(RECIPIENT_ID, NOTIFICATION_ID);

        assertThat(result).isEqualTo(response);
        assertThat(n.getReadAt()).isEqualTo(firstReadAt);
        verify(notificationRepository, never()).saveAndFlush(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_notOwnedByCaller_throwsNotFoundWithoutRevealingExistence() {
        when(notificationRepository.findByIdAndRecipientId(NOTIFICATION_ID, OTHER_USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(OTHER_USER_ID, NOTIFICATION_ID))
                .isInstanceOf(NotificationNotFoundException.class);

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void markAllAsRead_delegatesToTheBulkUpdateAndReturnsTheAffectedRowCount() {
        when(notificationRepository.markAllAsRead(eq(RECIPIENT_ID), any(LocalDateTime.class)))
                .thenReturn(5);

        int result = service.markAllAsRead(RECIPIENT_ID);

        assertThat(result).isEqualTo(5);
        ArgumentCaptor<LocalDateTime> timestampCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationRepository).markAllAsRead(eq(RECIPIENT_ID), timestampCaptor.capture());
        assertThat(timestampCaptor.getValue()).isNotNull();
    }

    @Test
    void delete_ownedNotification_deletesTheManagedEntityRatherThanByBareId() {
        Notification n = notification(RECIPIENT_ID, null);
        when(notificationRepository.findByIdAndRecipientId(NOTIFICATION_ID, RECIPIENT_ID))
                .thenReturn(Optional.of(n));

        service.delete(RECIPIENT_ID, NOTIFICATION_ID);

        verify(notificationRepository).delete(n);
        verify(notificationRepository, never()).deleteById(any());
    }

    @Test
    void delete_notOwnedByCaller_throwsNotFoundAndDeletesNothing() {
        when(notificationRepository.findByIdAndRecipientId(NOTIFICATION_ID, OTHER_USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(OTHER_USER_ID, NOTIFICATION_ID))
                .isInstanceOf(NotificationNotFoundException.class);

        verify(notificationRepository, never()).delete(any());
    }
}
