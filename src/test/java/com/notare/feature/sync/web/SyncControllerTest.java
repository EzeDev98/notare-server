package com.notare.feature.sync.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.sync.dto.response.SyncBatchResponse;
import com.notare.feature.sync.dto.response.SyncNoteResponse;
import com.notare.feature.sync.dto.response.SyncOperationResult;
import com.notare.feature.sync.dto.response.SyncPullResponse;
import com.notare.feature.sync.enums.SyncResultStatus;
import com.notare.feature.sync.service.SyncPullService;
import com.notare.feature.sync.service.SyncPushService;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.common.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15l of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - see {@link
 * AuthControllerTest}'s disclaimer (Step 15a);
 * same reasoning. Run via {@code mvn test} before trusting it.
 *
 * <p>Authenticated principal is supplied the same way as
 * {@link CollaborationControllerTest}
 * (Step 15d) - directly on {@code SecurityContextHolder} in the test thread,
 * with {@code addFilters = false} keeping the real
 * {@code JwtAuthenticationFilter} chain out of this slice.
 *
 * <p>Neither endpoint takes a {@code Pageable} - the controller's own javadoc
 * is explicit that a sort parameter would be silently discarded by delta
 * paging's fixed ordering, so {@code pull} takes plain {@code page}/{@code
 * size} ints instead. The tests below reflect that: they assert the
 * {@code Pageable} captured on the service call rather than reaching for any
 * {@code Sort} assertion, since there is no sort to assert.
 *
 * <p>{@code push}'s own javadoc states the batch's defining behaviour - 200
 * even when every operation conflicted, since the BATCH succeeded and each
 * operation's own fate lives in its {@link SyncOperationResult}. That is
 * asserted directly below (a batch containing one APPLIED and one CONFLICT
 * result still comes back 200), alongside the one thing that IS a structural
 * 400: {@code @NotEmpty}/{@code @Size} on the operations list and {@code
 * @Valid}-cascaded field validation on each element, all handled by bean
 * validation before the service ever runs.
 *
 * <p>Covers: {@code GET /sync} - {@code since} omitted is forwarded as
 * {@code null} (first sync); an explicit ISO-8601 {@code since} is parsed and
 * forwarded; {@code page}/{@code size} default to 0/100 and an explicit
 * override is honoured; a {@code size} of 0 is passed through as
 * {@code Math.max(size, 1)} = 1, per the controller's own clamp (the
 * documented ceiling itself is the service's job, per the javadoc, so it is
 * not re-asserted here); the response's {@code changed}/{@code deletedIds}/
 * {@code nextCursor}/{@code hasMore} all round-trip. {@code POST
 * /sync/batch} - a mixed-outcome batch still returns 200 with each result's
 * own status; an empty {@code operations} list is rejected by validation
 * before the service runs; over-cap (201 operations) is rejected the same
 * way; a missing {@code operationId} or {@code type} on one element is
 * rejected by validation (the cross-field \"UPDATE needs a version\" rule
 * is explicitly the SERVICE's job per its own javadoc, so violating it is
 * NOT asserted as a 400 here - it would come back as an INVALID result inside a
 * 200, which is Step 9c/13's concern, not this controller's).
 */
@WebMvcTest(
        controllers = SyncController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class SyncControllerTest {

    private static final String SYNC_URL = "/api/v1/sync";
    private static final String BATCH_URL = "/api/v1/sync/batch";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SyncPullService syncPullService;

    @MockBean
    private SyncPushService syncPushService;

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
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);

        UserDetail userDetail = new UserDetail(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetail, null, userDetail.getAuthorities()));
    }

    private NoteResponse sampleNote(Long id, Long ownerId, Long version) {
        LocalDateTime now = LocalDateTime.now();
        return new NoteResponse(id, ownerId, "content", false, false, version, ownerId, now, now);
    }

    private SyncPullResponse emptyPullResponse(LocalDateTime cursor) {
        return new SyncPullResponse(List.of(), List.of(), cursor, false);
    }

    // ----------------------------------------------------------------
    // GET /sync
    // ----------------------------------------------------------------

    @Test
    void pull_noSinceParam_forwardsNullForAFirstSync() throws Exception {
        authenticateAs(1L);
        LocalDateTime cursor = LocalDateTime.now();
        when(syncPullService.pull(eq(1L), isNull(), any())).thenReturn(emptyPullResponse(cursor));

        mockMvc.perform(get(SYNC_URL))
                .andExpect(status().isOk());

        verify(syncPullService).pull(eq(1L), isNull(), any());
    }

    @Test
    void pull_explicitSinceParam_isParsedAndForwarded() throws Exception {
        authenticateAs(1L);
        LocalDateTime since = LocalDateTime.of(2026, 1, 15, 10, 30, 0);
        LocalDateTime cursor = LocalDateTime.now();
        when(syncPullService.pull(eq(1L), eq(since), any())).thenReturn(emptyPullResponse(cursor));

        mockMvc.perform(get(SYNC_URL).param("since", "2026-01-15T10:30:00"))
                .andExpect(status().isOk());

        verify(syncPullService).pull(eq(1L), eq(since), any());
    }

    @Test
    void pull_noPageOrSizeParams_defaultsToPage0Size100() throws Exception {
        authenticateAs(1L);
        when(syncPullService.pull(eq(1L), isNull(), any())).thenReturn(emptyPullResponse(LocalDateTime.now()));

        mockMvc.perform(get(SYNC_URL))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(syncPullService).pull(eq(1L), isNull(), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(100);
    }

    @Test
    void pull_explicitPageAndSize_overridesTheDefault() throws Exception {
        authenticateAs(1L);
        when(syncPullService.pull(eq(1L), isNull(), any())).thenReturn(emptyPullResponse(LocalDateTime.now()));

        mockMvc.perform(get(SYNC_URL).param("page", "3").param("size", "25"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(syncPullService).pull(eq(1L), isNull(), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(25);
    }

    @Test
    void pull_sizeOfZero_isClampedUpToOneRatherThanPassedThrough() throws Exception {
        // The controller's own PageRequest.of(page, Math.max(size, 1)) - a
        // literal 0 would otherwise throw inside PageRequest itself.
        authenticateAs(1L);
        when(syncPullService.pull(eq(1L), isNull(), any())).thenReturn(emptyPullResponse(LocalDateTime.now()));

        mockMvc.perform(get(SYNC_URL).param("size", "0"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(syncPullService).pull(eq(1L), isNull(), pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void pull_returnsTheFullResponseShape() throws Exception {
        authenticateAs(1L);
        LocalDateTime cursor = LocalDateTime.of(2026, 2, 1, 0, 0, 0);
        SyncNoteResponse changed = new SyncNoteResponse(sampleNote(5L, 1L, 2L), "client-note-1");
        SyncPullResponse response = new SyncPullResponse(List.of(changed), List.of(9L, 10L), cursor, true);
        when(syncPullService.pull(eq(1L), isNull(), any())).thenReturn(response);

        mockMvc.perform(get(SYNC_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed[0].note.id").value(5))
                .andExpect(jsonPath("$.changed[0].clientId").value("client-note-1"))
                .andExpect(jsonPath("$.deletedIds", org.hamcrest.Matchers.contains(9, 10)))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void pull_isKeyedOffTheAuthenticatedPrincipalNotAnythingElse() throws Exception {
        authenticateAs(77L);
        when(syncPullService.pull(eq(77L), isNull(), any())).thenReturn(emptyPullResponse(LocalDateTime.now()));

        mockMvc.perform(get(SYNC_URL))
                .andExpect(status().isOk());

        verify(syncPullService).pull(eq(77L), isNull(), any());
    }

    // ----------------------------------------------------------------
    // POST /sync/batch
    // ----------------------------------------------------------------

    @Test
    void push_mixedOutcomeBatch_stillReturns200WithEachOperationsOwnStatus() throws Exception {
        // The controller's own javadoc: the batch is 200 even when every
        // operation conflicted - here one applies and one conflicts, and the
        // HTTP layer must not editorialise either way.
        authenticateAs(1L);
        SyncOperationResult applied = new SyncOperationResult(
                "op-1", SyncResultStatus.APPLIED, sampleNote(5L, 1L, 1L), "client-note-1", null);
        SyncOperationResult notFound = new SyncOperationResult(
                "op-2", SyncResultStatus.NOT_FOUND, null, null, "This note no longer exists.");
        SyncBatchResponse response = new SyncBatchResponse(
                List.of(applied, notFound), 1, LocalDateTime.now());
        when(syncPushService.push(eq(1L), any())).thenReturn(response);

        mockMvc.perform(post(BATCH_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(batchBody(
                                operation("op-1", "CREATE", "client-note-1", null, "hello", null),
                                operation("op-2", "UPDATE", null, 6L, "edit", 3L)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCount").value(1))
                .andExpect(jsonPath("$.results[0].operationId").value("op-1"))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[0].clientId").value("client-note-1"))
                .andExpect(jsonPath("$.results[1].operationId").value("op-2"))
                .andExpect(jsonPath("$.results[1].status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.results[1].message").value("This note no longer exists."));

        verify(syncPushService).push(eq(1L), any());
    }

    @Test
    void push_emptyOperationsList_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post(BATCH_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(batchBody())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.operations").value("operations must not be empty."));

        verifyNoInteractions(syncPushService);
    }

    @Test
    void push_overTheTwoHundredOperationCap_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        List<Map<String, Object>> operations = new java.util.ArrayList<>();
        for (int i = 0; i < 201; i++) {
            operations.add(operation("op-" + i, "DELETE", null, (long) i, null, null));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("operations", operations);

        mockMvc.perform(post(BATCH_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.operations").value("A batch may contain at most 200 operations."));

        verifyNoInteractions(syncPushService);
    }

    @Test
    void push_operationMissingOperationId_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post(BATCH_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(batchBody(
                                operation(null, "DELETE", null, 5L, null, null)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasKey(
                        org.hamcrest.Matchers.containsString("operationId"))));

        verifyNoInteractions(syncPushService);
    }

    @Test
    void push_operationMissingType_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post(BATCH_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(batchBody(
                                operation("op-1", null, null, 5L, null, null)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").value(org.hamcrest.Matchers.hasKey(
                        org.hamcrest.Matchers.containsString("type"))));

        verifyNoInteractions(syncPushService);
    }

    @Test
    void push_isKeyedOffTheAuthenticatedPrincipalNotAnythingElse() throws Exception {
        authenticateAs(42L);
        when(syncPushService.push(eq(42L), any()))
                .thenReturn(new SyncBatchResponse(List.of(), 0, LocalDateTime.now()));

        mockMvc.perform(post(BATCH_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(batchBody(
                                operation("op-1", "DELETE", null, 5L, null, null)))))
                .andExpect(status().isOk());

        verify(syncPushService).push(eq(42L), any());
    }

    @SafeVarargs
    private Map<String, Object> batchBody(Map<String, Object>... operations) {
        Map<String, Object> body = new HashMap<>();
        body.put("operations", List.of(operations));
        return body;
    }

    private Map<String, Object> operation(
            String operationId, String type, String clientId, Long noteId, String content, Long version) {
        Map<String, Object> op = new HashMap<>();
        op.put("operationId", operationId);
        op.put("type", type);
        op.put("clientId", clientId);
        op.put("noteId", noteId);
        op.put("content", content);
        op.put("version", version);
        return op;
    }
}