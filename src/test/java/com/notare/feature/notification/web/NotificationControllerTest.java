package com.notare.feature.notification.web;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.NotificationNotFoundException;
import com.notare.feature.notification.dto.response.NotificationResponse;
import com.notare.feature.notification.dto.response.UnreadCountResponse;
import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import com.notare.feature.notification.service.NotificationService;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15g of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - see {@link
 * com.notare.feature.auth.web.AuthControllerTest}'s disclaimer (Step 15a);
 * same reasoning. Run via {@code mvn test} before trusting it.
 *
 * <p>Authenticated principal is supplied the same way as
 * {@link com.notare.feature.collaboration.web.CollaborationControllerTest}
 * (Step 15d) - directly on {@code SecurityContextHolder} in the test
 * thread, with {@code addFilters = false} keeping the real
 * {@code JwtAuthenticationFilter} chain out of this slice.
 *
 * <p>Every endpoint here is implicitly scoped to the caller per
 * {@link NotificationController}'s own javadoc - there is no recipient path
 * variable to test for tampering, so each test's emphasis is on (a) the
 * authenticated user's id reaching the service and nobody else's, (b) the
 * {@code unreadOnly} filter and the {@code @PageableDefault} (size 20, sort
 * {@code createdAt} DESC) on the list endpoint, and (c)
 * {@link NotificationNotFoundException} -&gt; 404 on the two id-based
 * mutations.
 *
 * <p>Covers: {@code GET /notifications} - default paging/sort, an explicit
 * override, {@code unreadOnly=true} forwarded, and the
 * {@link PagedResponse} envelope. {@code GET /notifications/unread-count} -
 * 200 with the wrapped count. {@code POST /notifications/{id}/read} - 200
 * with the updated notification, and unknown/not-mine -&gt; 404.
 * {@code POST /notifications/read-all} - 200 wrapping the service's
 * marked-count in {@link com.notare.feature.notification.dto.response.MarkAllReadResponse},
 * including the zero case. {@code DELETE /notifications/{id}} - 204, and
 * unknown/not-mine -&gt; 404.
 */
@WebMvcTest(controllers = NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    private static final String BASE_URL = "/api/v1/notifications";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("jdoe");
        user.setNotarianId("NOT-ABCDEF");
        user.setEmail("jdoe@example.com");
        user.setPassword("hashed");
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);

        UserDetail userDetail = new UserDetail(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetail, null, userDetail.getAuthorities()));
    }

    private NotificationResponse sampleNotification(Long id, LocalDateTime readAt) {
        return new NotificationResponse(
                id, NotificationType.NOTE_SHARED, 2L, NotificationTargetType.NOTE, 10L,
                "Project Plan", readAt, LocalDateTime.now());
    }

    private PagedResponse<NotificationResponse> emptyPage() {
        Page<NotificationResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        return PagedResponse.of(List.of(), page);
    }

    // ----------------------------------------------------------------
    // GET /notifications
    // ----------------------------------------------------------------

    @Test
    void list_noParams_defaultsToUnreadOnlyFalseAndPageableDefaultOfSize20SortedByCreatedAtDesc() throws Exception {
        authenticateAs(1L);
        when(notificationService.list(eq(1L), eq(false), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());

        ArgumentCaptor<Boolean> unreadOnlyCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).list(eq(1L), unreadOnlyCaptor.capture(), pageableCaptor.capture());

        assertThat(unreadOnlyCaptor.getValue()).isFalse();
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void list_unreadOnlyTrue_isForwardedToTheService() throws Exception {
        authenticateAs(1L);
        when(notificationService.list(eq(1L), eq(true), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL).param("unreadOnly", "true"))
                .andExpect(status().isOk());

        verify(notificationService).list(eq(1L), eq(true), any());
    }

    @Test
    void list_explicitPagingParams_overridesTheDefault() throws Exception {
        authenticateAs(1L);
        when(notificationService.list(eq(1L), eq(false), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL)
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).list(eq(1L), eq(false), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        Sort.Order order = pageable.getSort().getOrderFor("id");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void list_returnsThePagedResponseEnvelopeFromTheService() throws Exception {
        authenticateAs(1L);
        NotificationResponse row = sampleNotification(9L, null);
        Page<NotificationResponse> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        when(notificationService.list(eq(1L), eq(false), any()))
                .thenReturn(PagedResponse.of(List.of(row), page));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(9))
                .andExpect(jsonPath("$.content[0].type").value("NOTE_SHARED"))
                .andExpect(jsonPath("$.content[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_isKeyedOffTheCallerNotAQueryParam() throws Exception {
        authenticateAs(77L);
        when(notificationService.list(anyLong(), eq(false), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());

        verify(notificationService).list(eq(77L), eq(false), any());
    }

    // ----------------------------------------------------------------
    // GET /notifications/unread-count
    // ----------------------------------------------------------------

    @Test
    void unreadCount_validRequest_returns200WithTheWrappedCount() throws Exception {
        authenticateAs(1L);
        when(notificationService.unreadCount(1L)).thenReturn(new UnreadCountResponse(7L));

        mockMvc.perform(get(BASE_URL + "/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(7));
    }

    @Test
    void unreadCount_neverConflictsWithTheIdRoute() throws Exception {
        // Literal /unread-count must win over the (nonexistent, but
        // structurally similar) /{notificationId} pattern used elsewhere.
        authenticateAs(1L);
        when(notificationService.unreadCount(1L)).thenReturn(new UnreadCountResponse(0L));

        mockMvc.perform(get(BASE_URL + "/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    // ----------------------------------------------------------------
    // POST /notifications/{id}/read
    // ----------------------------------------------------------------

    @Test
    void markAsRead_validRequest_returns200WithTheUpdatedNotification() throws Exception {
        authenticateAs(1L);
        LocalDateTime readAt = LocalDateTime.now();
        when(notificationService.markAsRead(1L, 9L)).thenReturn(sampleNotification(9L, readAt));

        mockMvc.perform(post(BASE_URL + "/9/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.readAt").exists());

        verify(notificationService).markAsRead(1L, 9L);
    }

    @Test
    void markAsRead_unknownOrNotMine_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(notificationService.markAsRead(1L, 999L))
                .thenThrow(new NotificationNotFoundException("Notification not found."));

        mockMvc.perform(post(BASE_URL + "/999/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Notification not found."));
    }

    // ----------------------------------------------------------------
    // POST /notifications/read-all
    // ----------------------------------------------------------------

    @Test
    void markAllAsRead_someUnread_returns200WithTheMarkedCount() throws Exception {
        authenticateAs(1L);
        when(notificationService.markAllAsRead(1L)).thenReturn(4);

        mockMvc.perform(post(BASE_URL + "/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedCount").value(4));

        verify(notificationService).markAllAsRead(1L);
    }

    @Test
    void markAllAsRead_nothingUnread_returns200WithZero() throws Exception {
        authenticateAs(1L);
        when(notificationService.markAllAsRead(1L)).thenReturn(0);

        mockMvc.perform(post(BASE_URL + "/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedCount").value(0));
    }

    // ----------------------------------------------------------------
    // DELETE /notifications/{id}
    // ----------------------------------------------------------------

    @Test
    void delete_validRequest_returns204() throws Exception {
        authenticateAs(1L);
        doNothing().when(notificationService).delete(1L, 9L);

        mockMvc.perform(delete(BASE_URL + "/9"))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());

        verify(notificationService).delete(1L, 9L);
    }

    @Test
    void delete_unknownOrNotMine_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        org.mockito.Mockito.doThrow(new NotificationNotFoundException("Notification not found."))
                .when(notificationService).delete(1L, 999L);

        mockMvc.perform(delete(BASE_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Notification not found."));
    }
}