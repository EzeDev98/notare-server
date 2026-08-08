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

/**
 * A user's own notification inbox.
 *
 * <p>Top-level collection, and every endpoint is implicitly scoped to the
 * caller — there is no /users/{id}/notifications, because you can only ever
 * read your own. A path variable for the recipient would just be an
 * authorization check waiting to be forgotten.
 *
 * <p><b>There is no POST /notifications.</b> Notifications are created by
 * events, never by clients. An endpoint that let one user create a notification
 * for another is a spam vector with a feature's name on it, so the write side
 * simply isn't exposed — NotificationService.create exists only for the 8D
 * listeners.
 *
 * <p>Protected by default via the security allowlist's closing
 * anyRequest().authenticated().
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * My notifications, newest first.
     *
     * <p>{@code ?unreadOnly=true} narrows to unread. A query parameter rather
     * than a separate /unread sub-path: this is a FILTER over one collection,
     * not a different collection — same shape, same paging, same sort, one
     * predicate different. Contrast /collaborations/sent and /received in
     * Step 6, which really are two different queries over two different indexed
     * columns and earned their own paths.
     */
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

    /**
     * The badge count on its own.
     *
     * <p>A dedicated endpoint rather than making clients fetch a page and read
     * totalElements: this is polled far more often than anything else here, and
     * it's answered from an index without materialising a single row. Asking for
     * 20 notifications to learn one number is the kind of waste that only shows
     * up under load.
     *
     * <p>No routing ambiguity with a future GET /notifications/{id}: Spring
     * matches a literal path segment ahead of a template, so "unread-count"
     * always wins.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal UserDetail userDetail) {

        return ResponseEntity.ok(notificationService.unreadCount(userDetail.getUser().getId()));
    }

    /**
     * Mark one as read.
     *
     * <p>POST on an action sub-path, same convention as the collaboration
     * accept/reject in Step 6 — it's a named transition, not an arbitrary field
     * edit. PATCH with {@code {"read": true}} would invite clients to write
     * readAt directly, including backdating it.
     *
     * <p>Idempotent: already-read returns 200 unchanged, and the original
     * timestamp is preserved rather than restamped.
     */
    @PostMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long notificationId) {

        NotificationResponse response = notificationService.markAsRead(
                userDetail.getUser().getId(), notificationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark everything read. Returns how many rows changed, so the client can
     * settle its badge in one round trip.
     *
     * <p>A collection-level action, so it sits at /read-all rather than under an
     * id. No clash with /{notificationId}/read — different path shapes.
     */
    @PostMapping("/read-all")
    public ResponseEntity<MarkAllReadResponse> markAllAsRead(
            @AuthenticationPrincipal UserDetail userDetail) {

        int marked = notificationService.markAllAsRead(userDetail.getUser().getId());
        return ResponseEntity.ok(new MarkAllReadResponse(marked));
    }

    /**
     * Remove one notification from my inbox. 204 — nothing left to return.
     *
     * <p>A real delete, not a soft one. Notes are soft-deleted because losing
     * one is a disaster and users expect a trash; dismissing a notification is
     * meant to be final, and a "deleted notifications" list is not a feature
     * anyone wants. Different data, different answer.
     */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long notificationId) {

        notificationService.delete(userDetail.getUser().getId(), notificationId);
        return ResponseEntity.noContent().build();
    }
}