package com.notare.feature.notification.web;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.notification.dto.response.MarkAllReadResponse;
import com.notare.feature.notification.dto.response.NotificationResponse;
import com.notare.feature.notification.dto.response.UnreadCountResponse;
import com.notare.feature.notification.service.NotificationService;
import com.notare.feature.user.model.UserDetail;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the caller's notification inbox. */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<PagedResponse<NotificationResponse>> list(
            @AuthenticationPrincipal UserDetail userDetail,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        PagedResponse<NotificationResponse> response = notificationService.list(
                userDetail.getUser().getId(), unreadOnly, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal UserDetail userDetail) {

        return ResponseEntity.ok(notificationService.unreadCount(userDetail.getUser().getId()));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long notificationId) {

        NotificationResponse response = notificationService.markAsRead(
                userDetail.getUser().getId(), notificationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/read-all")
    public ResponseEntity<MarkAllReadResponse> markAllAsRead(
            @AuthenticationPrincipal UserDetail userDetail) {

        int marked = notificationService.markAllAsRead(userDetail.getUser().getId());
        return ResponseEntity.ok(new MarkAllReadResponse(marked));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long notificationId) {

        notificationService.delete(userDetail.getUser().getId(), notificationId);
        return ResponseEntity.noContent().build();
    }
}
