package com.notare.feature.share.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InsufficientNotePermissionException;
import com.notare.common.exception.NoteConflictException;
import com.notare.common.exception.NoteShareNotFoundException;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.share.dto.response.SharedNoteSummaryResponse;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.service.SharedNoteService;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.common.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15k of the coverage roadmap.
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
 * <p>{@link SharedNoteController} builds the upload endpoint's Location
 * header by hand from an {@code @Value("${app.title}")}-injected field
 * rather than a declarative {@code @RequestMapping} placeholder - per the
 * controller's own inline comment, this is deliberate so the header stays
 * correct if {@code app.title} ever changes. {@code @WebMvcTest} still loads
 * the real {@code Environment}, so {@code application.properties}'
 * {@code app.title=api/${app.api-version}} (-&gt; {@code api/v1}) resolves
 * for real here, and {@code uploadVoiceNote_...} below is this file's
 * equivalent of {@code CollaborationControllerTest}'s Location assertion
 * (Step 15d) - just against a hand-built URI instead of an annotation-derived
 * one.
 *
 * <p>Every failure this controller can hit funnels through one gate in the
 * service, {@code requireGrant}, and its own javadoc states the two outcomes
 * precisely: no grant at all is a 404 ({@link NoteShareNotFoundException},
 * indistinguishable from the note not existing - deliberately, so a stranger
 * can't map out ids), and a grant that exists but is too weak for what's
 * attempted is a 403 ({@link InsufficientNotePermissionException} - the
 * caller can already see the note, so hiding it here would be incoherent).
 * Both are asserted at least once for a READ-only recipient attempting a
 * write, and the 404 is asserted once more standing in for every other
 * endpoint that resolves the same grant.
 *
 * <p>Covers: {@code GET /shared-notes} - the {@code @PageableDefault} (size
 * 20, sort {@code createdAt} DESC - the grant's own timestamp, not the
 * note's, per the controller's own javadoc caveat), an explicit override, and
 * the {@link PagedResponse} envelope. {@code GET /shared-notes/{noteId}} -
 * 200, and the 404 for no grant. {@code PUT /shared-notes/{noteId}} - happy
 * path, missing {@code version} rejected by validation exactly as the
 * owner's own PUT is (Step 15f), {@link NoteConflictException} -&gt; 409 with
 * {@code currentVersion}, and 403 for a READ-only recipient. {@code PATCH
 * /shared-notes/{noteId}} - the partial-update happy path and its own
 * missing-version 400. {@code GET .../voice-notes} - the full list,
 * regardless of who recorded each one. {@code POST .../voice-notes} - 201
 * with the hand-built Location, the file and duration forwarded, and 403 for
 * a READ-only recipient (recording a reply is an edit). {@code GET
 * .../voice-notes/{id}/download} - the Content-Disposition filename falling
 * back to {@code "voice-note-{id}"} when {@code originalFilename} is null,
 * per the controller's own inline fallback. {@code GET
 * .../voice-notes/{id}/stream} - no {@code Range} header -&gt; 200
 * with the whole body and {@code Accept-Ranges}; a {@code Range} header -&gt; 206
 * with a slice, capped at the controller's 1&nbsp;MB {@code CHUNK_SIZE}.
 */
@WebMvcTest(
        controllers = SharedNoteController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
public class SharedNoteControllerTest {

    private static final String BASE_URL = "/api/v1/shared-notes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SharedNoteService sharedNoteService;

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
        return new NoteResponse(id, ownerId, content, false, false, version, ownerId, now, now);
    }

    private SharedNoteSummaryResponse sampleSharedSummary(Long shareId, Long noteId, Long ownerId) {
        NoteSummaryResponse note = new NoteSummaryResponse(noteId, "preview...", false, false, 1L, LocalDateTime.now());
        return new SharedNoteSummaryResponse(shareId, note, ownerId, SharePermission.READ, LocalDateTime.now());
    }

    private PagedResponse<SharedNoteSummaryResponse> emptyPage() {
        Page<SharedNoteSummaryResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        return PagedResponse.of(List.of(), page);
    }

    private VoiceNoteResponse sampleVoiceNote(Long id, Long ownerId, Long noteId) {
        LocalDateTime now = LocalDateTime.now();
        return new VoiceNoteResponse(id, ownerId, noteId, "reply.m4a", "audio/mp4", 2048L, 12, now, now);
    }

    // ----------------------------------------------------------------
    // GET /shared-notes
    // ----------------------------------------------------------------

    @Test
    void listSharedWithMe_noParams_usesThePageableDefaultOfSize20SortedByCreatedAtDesc() throws Exception {
        authenticateAs(1L);
        when(sharedNoteService.listSharedWithMe(eq(1L), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(sharedNoteService).listSharedWithMe(eq(1L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listSharedWithMe_explicitPagingParams_overridesTheDefault() throws Exception {
        authenticateAs(1L);
        when(sharedNoteService.listSharedWithMe(eq(1L), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL).param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(sharedNoteService).listSharedWithMe(eq(1L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
    }

    @Test
    void listSharedWithMe_returnsThePagedResponseEnvelopeFromTheService() throws Exception {
        authenticateAs(1L);
        SharedNoteSummaryResponse row = sampleSharedSummary(3L, 7L, 2L);
        Page<SharedNoteSummaryResponse> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        when(sharedNoteService.listSharedWithMe(eq(1L), any()))
                .thenReturn(PagedResponse.of(List.of(row), page));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].shareId").value(3))
                .andExpect(jsonPath("$.content[0].note.id").value(7))
                .andExpect(jsonPath("$.content[0].ownerId").value(2))
                .andExpect(jsonPath("$.content[0].permission").value("READ"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ----------------------------------------------------------------
    // GET /shared-notes/{noteId}
    // ----------------------------------------------------------------

    @Test
    void getSharedNote_validRequest_returns200WithTheNote() throws Exception {
        authenticateAs(1L);
        when(sharedNoteService.getSharedNote(1L, 7L)).thenReturn(sampleNote(7L, 2L, "hello", 0L));

        mockMvc.perform(get(BASE_URL + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.content").value("hello"));
    }

    @Test
    void getSharedNote_noGrant_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(sharedNoteService.getSharedNote(1L, 999L))
                .thenThrow(new NoteShareNotFoundException("Note not found."));

        mockMvc.perform(get(BASE_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Note not found."));
    }

    // ----------------------------------------------------------------
    // PUT /shared-notes/{noteId}
    // ----------------------------------------------------------------

    @Test
    void updateSharedNote_validRequest_returns200WithTheUpdatedNote() throws Exception {
        authenticateAs(1L);
        when(sharedNoteService.updateSharedNote(eq(1L), eq(7L), any()))
                .thenReturn(sampleNote(7L, 2L, "edited", 1L));

        mockMvc.perform(put(BASE_URL + "/7")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateBody("edited", 0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("edited"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updateSharedNote_missingVersion_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        Map<String, Object> body = new HashMap<>();
        body.put("content", "edited");

        mockMvc.perform(put(BASE_URL + "/7")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").value("Version is required so we can detect edit conflicts."));

        verifyNoInteractions(sharedNoteService);
    }

    @Test
    void updateSharedNote_staleVersion_returns409WithCurrentVersionExposed() throws Exception {
        authenticateAs(1L);
        when(sharedNoteService.updateSharedNote(eq(1L), eq(7L), any()))
                .thenThrow(new NoteConflictException("This note was modified elsewhere.", 5L));

        mockMvc.perform(put(BASE_URL + "/7")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateBody("stale", 1L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentVersion").value(5));
    }

    @Test
    void updateSharedNote_readOnlyRecipient_returns403ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(sharedNoteService.updateSharedNote(eq(1L), eq(7L), any()))
                .thenThrow(new InsufficientNotePermissionException("You have read-only access to this note."));

        mockMvc.perform(put(BASE_URL + "/7")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateBody("edited", 0L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You have read-only access to this note."));
    }

    // ----------------------------------------------------------------
    // PATCH /shared-notes/{noteId} (auto-save)
    // ----------------------------------------------------------------

    @Test
    void autoSaveSharedNote_partialUpdate_returns200() throws Exception {
        authenticateAs(1L);
        when(sharedNoteService.autoSaveSharedNote(eq(1L), eq(7L), any()))
                .thenReturn(sampleNote(7L, 2L, "unchanged", 2L));

        Map<String, Object> body = new HashMap<>();
        body.put("version", 1L);

        mockMvc.perform(patch(BASE_URL + "/7")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void autoSaveSharedNote_missingVersion_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(patch(BASE_URL + "/7")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").value("Version is required so we can detect edit conflicts."));

        verifyNoInteractions(sharedNoteService);
    }

    // ----------------------------------------------------------------
    // GET /shared-notes/{noteId}/voice-notes
    // ----------------------------------------------------------------

    @Test
    void listAttachedVoiceNotes_validRequest_returns200WithEveryAttachedRecording() throws Exception {
        authenticateAs(1L);
        when(sharedNoteService.listAttachedVoiceNotes(1L, 7L))
                .thenReturn(List.of(sampleVoiceNote(11L, 2L, 7L), sampleVoiceNote(12L, 1L, 7L)));

        mockMvc.perform(get(BASE_URL + "/7/voice-notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].ownerId").value(2))
                .andExpect(jsonPath("$[1].ownerId").value(1));
    }

    @Test
    void listAttachedVoiceNotes_noGrant_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(sharedNoteService.listAttachedVoiceNotes(1L, 999L))
                .thenThrow(new NoteShareNotFoundException("Note not found."));

        mockMvc.perform(get(BASE_URL + "/999/voice-notes"))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------
    // POST /shared-notes/{noteId}/voice-notes
    // ----------------------------------------------------------------

    @Test
    void uploadVoiceNote_validRequest_returns201WithHandBuiltLocationAndForwardsFileAndDuration() throws Exception {
        authenticateAs(1L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "reply.m4a", "audio/mp4", "fake-audio-bytes".getBytes(StandardCharsets.UTF_8));
        when(sharedNoteService.uploadVoiceNote(eq(1L), eq(7L), any(), eq(12)))
                .thenReturn(sampleVoiceNote(9L, 1L, 7L));

        mockMvc.perform(multipart(BASE_URL + "/7/voice-notes")
                        .file(file)
                        .param("durationSeconds", "12"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/")))
                .andExpect(header().string("Location", endsWith("/shared-voice-notes/9")))
                .andExpect(jsonPath("$.id").value(9));

        verify(sharedNoteService).uploadVoiceNote(eq(1L), eq(7L), any(), eq(12));
    }

    @Test
    void uploadVoiceNote_durationSecondsOmitted_forwardsNull() throws Exception {
        authenticateAs(1L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "reply.m4a", "audio/mp4", "fake-audio-bytes".getBytes(StandardCharsets.UTF_8));
        when(sharedNoteService.uploadVoiceNote(eq(1L), eq(7L), any(), isNull()))
                .thenReturn(sampleVoiceNote(9L, 1L, 7L));

        mockMvc.perform(multipart(BASE_URL + "/7/voice-notes").file(file))
                .andExpect(status().isCreated());

        verify(sharedNoteService).uploadVoiceNote(eq(1L), eq(7L), any(), isNull());
    }

    @Test
    void uploadVoiceNote_readOnlyRecipient_returns403ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "reply.m4a", "audio/mp4", "fake-audio-bytes".getBytes(StandardCharsets.UTF_8));
        when(sharedNoteService.uploadVoiceNote(eq(1L), eq(7L), any(), anyInt()))
                .thenThrow(new InsufficientNotePermissionException("You have read-only access to this note."));

        mockMvc.perform(multipart(BASE_URL + "/7/voice-notes")
                        .file(file)
                        .param("durationSeconds", "5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You have read-only access to this note."));
    }

    // ----------------------------------------------------------------
    // GET /shared-notes/{noteId}/voice-notes/{voiceNoteId}/download
    // ----------------------------------------------------------------

    @Test
    void downloadAttachedVoiceNote_validRequest_returns200WithHeadersAndBody() throws Exception {
        authenticateAs(1L);
        byte[] bytes = "fake-audio-bytes".getBytes(StandardCharsets.UTF_8);
        VoiceNoteContent content = new VoiceNoteContent(
                new ByteArrayResource(bytes), "audio/mp4", "reply.m4a", bytes.length);
        when(sharedNoteService.getAttachedVoiceNoteAudio(1L, 7L, 11L)).thenReturn(content);

        mockMvc.perform(get(BASE_URL + "/7/voice-notes/11/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/mp4"))
                .andExpect(header().longValue("Content-Length", bytes.length))
                .andExpect(header().string("Content-Disposition", containsString("reply.m4a")));
    }

    @Test
    void downloadAttachedVoiceNote_noOriginalFilename_fallsBackToVoiceNoteIdBasedName() throws Exception {
        authenticateAs(1L);
        byte[] bytes = "fake-audio-bytes".getBytes(StandardCharsets.UTF_8);
        VoiceNoteContent content = new VoiceNoteContent(
                new ByteArrayResource(bytes), "audio/mp4", null, bytes.length);
        when(sharedNoteService.getAttachedVoiceNoteAudio(1L, 7L, 11L)).thenReturn(content);

        mockMvc.perform(get(BASE_URL + "/7/voice-notes/11/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("voice-note-11")));
    }

    @Test
    void downloadAttachedVoiceNote_noGrant_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(sharedNoteService.getAttachedVoiceNoteAudio(1L, 999L, 11L))
                .thenThrow(new NoteShareNotFoundException("Note not found."));

        mockMvc.perform(get(BASE_URL + "/999/voice-notes/11/download"))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------
    // GET /shared-notes/{noteId}/voice-notes/{voiceNoteId}/stream
    // ----------------------------------------------------------------

    @Test
    void streamAttachedVoiceNote_noRangeHeader_returns200WithTheWholeResourceAndAcceptRanges() throws Exception {
        authenticateAs(1L);
        byte[] bytes = "fake-audio-bytes".getBytes(StandardCharsets.UTF_8);
        VoiceNoteContent content = new VoiceNoteContent(
                new ByteArrayResource(bytes), "audio/mp4", "reply.m4a", bytes.length);
        when(sharedNoteService.getAttachedVoiceNoteAudio(1L, 7L, 11L)).thenReturn(content);

        mockMvc.perform(get(BASE_URL + "/7/voice-notes/11/stream"))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Type", "audio/mp4"));
    }

    @Test
    void streamAttachedVoiceNote_withRangeHeader_returns206WithASlice() throws Exception {
        authenticateAs(1L);
        byte[] bytes = new byte[2048];
        VoiceNoteContent content = new VoiceNoteContent(
                new ByteArrayResource(bytes), "audio/mp4", "reply.m4a", bytes.length);
        when(sharedNoteService.getAttachedVoiceNoteAudio(1L, 7L, 11L)).thenReturn(content);

        mockMvc.perform(get(BASE_URL + "/7/voice-notes/11/stream")
                        .header("Range", "bytes=0-1023"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Accept-Ranges", "bytes"));
    }

    @Test
    void streamAttachedVoiceNote_noGrant_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(sharedNoteService.getAttachedVoiceNoteAudio(1L, 999L, 11L))
                .thenThrow(new NoteShareNotFoundException("Note not found."));

        mockMvc.perform(get(BASE_URL + "/999/voice-notes/11/stream"))
                .andExpect(status().isNotFound());
    }

    private Map<String, Object> updateBody(String content, Long version) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("version", version);
        return body;
    }
}