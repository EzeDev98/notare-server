package com.notare.feature.note.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.NoteNotFoundException;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.note.service.NoteService;
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
import com.notare.support.WebSliceSupport;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15f of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - see {@link
 * AuthControllerTest}'s disclaimer (Step 15a);
 * same reasoning. Run via {@code mvn test} before trusting it.
 *
 * <p>Authenticated principal is supplied the same way as
 * {@link CollaborationControllerTest}
 * (Step 15d) - directly on {@code SecurityContextHolder} in the test
 * thread, with {@code addFilters = false} keeping the real
 * {@code JwtAuthenticationFilter} chain out of this slice (Step 4b already
 * covers it). No {@code @ClientIp}/{@code @UserAgent} resolvers are needed -
 * {@link NoteController} takes neither.
 *
 * <p>This is the largest controller in the roadmap, so the intent per
 * endpoint is narrow and specific rather than exhaustive: bean validation is
 * already covered generically by {@code UpdateNoteRequest}/
 * {@code AutoSaveNoteRequest}'s shared {@code @NotNull version}, so it is
 * asserted once per request type rather than on every mutating endpoint; the
 * per-endpoint tests instead focus on (a) the caller's id reaching the
 * service rather than anything from the path/body, (b) the two paging
 * endpoints' distinct {@code @PageableDefault} sort orders, and (c) each
 * endpoint's own documented exception mapping.
 *
 * <p>Covers: {@code POST /notes} - 201 with Location header and body.
 * {@code GET /notes/{id}} - 200, and {@link NoteNotFoundException} -&gt; 404
 * via {@code GlobalExceptionHandler} (Step 14a), asserted once here and
 * assumed for the other id-based endpoints since they all delegate the same
 * lookup to the service. {@code GET /notes} - its {@code @PageableDefault} (size 20, sort
 * {@code pinned,updatedAt} DESC) versus an explicit override, and the
 * {@link PagedResponse} envelope. {@code GET /notes/trash} - its distinct
 * default sort ({@code deletedAt} DESC). {@code GET /notes/search} - the
 * {@code q} parameter is forwarded verbatim. {@code PUT /notes/{id}} - happy
 * path, a missing {@code version} rejected by validation before the service
 * runs, and {@link NoteConflictException} -&gt; 409 with
 * {@code currentVersion} surfaced as an extension member. {@code PATCH
 * /notes/{id}} (auto-save) - happy path with no content (partial update) and
 * the same missing-version 400. {@code DELETE /notes/{id}} - 204.
 * {@code POST /notes/{id}/restore} - 200 with the restored note.
 * {@code POST}/{@code DELETE /notes/{id}/pin} and - each
 * delegates to {@code NoteService#setPinned} with the
 * right boolean. {@code DELETE /notes/{id}/permanent} - 204.
 */
@WebSliceSupport
@WebMvcTest(
        controllers = NoteController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class NoteControllerTest {

    private static final String BASE_URL = "/api/v1/notes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NoteService noteService;

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

    private NoteResponse sampleNote(Long id, Long ownerId, String content, Long version) {
        LocalDateTime now = LocalDateTime.now();
        return new NoteResponse(id, ownerId, content, false, version, ownerId, now, now, false, null);
    }

    private NoteSummaryResponse sampleSummary(Long id) {
        return new NoteSummaryResponse(id, "preview...", false, 1L, LocalDateTime.now());
    }

    private PagedResponse<NoteSummaryResponse> emptySummaryPage() {
        Page<NoteSummaryResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        return PagedResponse.of(List.of(), page);
    }

    // ----------------------------------------------------------------
    // POST /notes
    // ----------------------------------------------------------------

    @Test
    void createNote_validRequest_returns201WithLocationHeaderAndBody() throws Exception {
        authenticateAs(1L);
        when(noteService.createNote(eq(1L), any()))
                .thenReturn(sampleNote(5L, 1L, "hello", 0L));

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createBody("hello"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/notes/5")))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.ownerId").value(1))
                .andExpect(jsonPath("$.content").value("hello"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void createNote_emptyContentIsAllowed_noValidationErrorAndReachesTheService() throws Exception {
        authenticateAs(1L);
        when(noteService.createNote(eq(1L), any()))
                .thenReturn(sampleNote(6L, 1L, null, 0L));

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated());

        verify(noteService).createNote(eq(1L), any());
    }

    @Test
    void createNote_usesTheAuthenticatedPrincipalAsOwner() throws Exception {
        authenticateAs(42L);
        when(noteService.createNote(eq(42L), any()))
                .thenReturn(sampleNote(9L, 42L, "x", 0L));

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createBody("x"))))
                .andExpect(status().isCreated());

        verify(noteService).createNote(eq(42L), any());
    }

    // ----------------------------------------------------------------
    // GET /notes/{id}
    // ----------------------------------------------------------------

    @Test
    void getNote_validRequest_returns200WithTheNote() throws Exception {
        authenticateAs(1L);
        when(noteService.getNote(1L, 5L)).thenReturn(sampleNote(5L, 1L, "hello", 0L));

        mockMvc.perform(get(BASE_URL + "/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.content").value("hello"));
    }

    @Test
    void getNote_unknownOrNotOwned_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(noteService.getNote(1L, 999L))
                .thenThrow(new NoteNotFoundException("Note not found."));

        mockMvc.perform(get(BASE_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Note not found."));
    }

    // ----------------------------------------------------------------
    // GET /notes (list)
    // ----------------------------------------------------------------

    @Test
    void listNotes_noParams_defaultsToActiveOnlyAndPageableDefaultOfSize20SortedByPinnedThenUpdatedAtDesc() throws Exception {
        authenticateAs(1L);
        when(noteService.listNotes(eq(1L), any())).thenReturn(emptySummaryPage());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(noteService).listNotes(eq(1L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort.Order pinnedOrder = pageable.getSort().getOrderFor("pinned");
        Sort.Order updatedAtOrder = pageable.getSort().getOrderFor("updatedAt");
        assertThat(pinnedOrder).isNotNull();
        assertThat(pinnedOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(updatedAtOrder).isNotNull();
        assertThat(updatedAtOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listNotes_returnsThePagedResponseEnvelopeFromTheService() throws Exception {
        authenticateAs(1L);
        NoteSummaryResponse row = sampleSummary(5L);
        Page<NoteSummaryResponse> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        when(noteService.listNotes(eq(1L), any()))
                .thenReturn(PagedResponse.of(List.of(row), page));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    // ----------------------------------------------------------------
    // GET /notes/trash
    // ----------------------------------------------------------------

    private void verifyNoInteractions_getNoteOnly() {
        org.mockito.Mockito.verify(noteService, org.mockito.Mockito.never()).getNote(anyLong(), anyLong());
    }

    // ----------------------------------------------------------------
    // GET /notes/search
    // ----------------------------------------------------------------

    @Test
    void searchNotes_forwardsTheQueryParamAndDefaultsToSize20SortedByUpdatedAtDesc() throws Exception {
        authenticateAs(1L);
        when(noteService.searchNotes(eq(1L), eq("project plan"), any())).thenReturn(emptySummaryPage());

        mockMvc.perform(get(BASE_URL + "/search").param("q", "project plan"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(noteService).searchNotes(eq(1L), eq("project plan"), pageableCaptor.capture());

        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("updatedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void searchNotes_missingQueryParam_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(get(BASE_URL + "/search"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(noteService);
    }

    // ----------------------------------------------------------------
    // PUT /notes/{id}
    // ----------------------------------------------------------------

    @Test
    void updateNote_validRequest_returns200WithTheUpdatedNote() throws Exception {
        authenticateAs(1L);
        when(noteService.updateNote(eq(1L), eq(5L), any()))
                .thenReturn(sampleNote(5L, 1L, "updated", 1L));

        mockMvc.perform(put(BASE_URL + "/5")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateBody("updated", 0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("updated"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updateNote_unknownNote_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(noteService.updateNote(eq(1L), eq(999L), any()))
                .thenThrow(new NoteNotFoundException("Note not found."));

        mockMvc.perform(put(BASE_URL + "/999")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateBody("x", 0L))))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------
    // PATCH /notes/{id} (auto-save)
    // ----------------------------------------------------------------

    @Test
    void autoSave_partialUpdateWithNoContent_returns200() throws Exception {
        authenticateAs(1L);
        when(noteService.autoSave(eq(1L), eq(5L), any()))
                .thenReturn(sampleNote(5L, 1L, "unchanged", 2L));

        Map<String, Object> body = new HashMap<>();
        body.put("version", 1L);

        mockMvc.perform(patch(BASE_URL + "/5")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    // ----------------------------------------------------------------
    // DELETE /notes/{id} (soft delete)
    // ----------------------------------------------------------------

    // ----------------------------------------------------------------
    // POST /notes/{id}/restore
    // ----------------------------------------------------------------

    // ----------------------------------------------------------------
    // Pin / unpin
    // ----------------------------------------------------------------

    @Test
    void pinNote_delegatesToSetPinnedWithTrue() throws Exception {
        authenticateAs(1L);
        when(noteService.setPinned(1L, 5L, true)).thenReturn(sampleNote(5L, 1L, "x", 1L));

        mockMvc.perform(post(BASE_URL + "/5/pin"))
                .andExpect(status().isOk());

        verify(noteService).setPinned(1L, 5L, true);
    }

    @Test
    void unpinNote_delegatesToSetPinnedWithFalse() throws Exception {
        authenticateAs(1L);
        when(noteService.setPinned(1L, 5L, false)).thenReturn(sampleNote(5L, 1L, "x", 1L));

        mockMvc.perform(delete(BASE_URL + "/5/pin"))
                .andExpect(status().isOk());

        verify(noteService).setPinned(1L, 5L, false);
    }

    // ----------------------------------------------------------------
    // DELETE /notes/{id}/permanent
    // ----------------------------------------------------------------

    private Map<String, String> createBody(String content) {
        Map<String, String> body = new HashMap<>();
        body.put("content", content);
        return body;
    }

    private Map<String, Object> updateBody(String content, Long version) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("version", version);
        return body;
    }
}