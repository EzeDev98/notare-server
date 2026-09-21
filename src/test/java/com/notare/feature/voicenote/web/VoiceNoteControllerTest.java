package com.notare.feature.voicenote.web;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InvalidVoiceUploadException;
import com.notare.common.exception.UnsupportedAudioFormatException;
import com.notare.common.exception.VoiceFileTooLargeException;
import com.notare.common.exception.VoiceNoteNotFoundException;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15p of the coverage roadmap.
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
 * <p>Upload validation ({@code VoiceUploadValidator}) runs INSIDE the
 * service, not the controller, so this controller has no bean-validated
 * request DTO the way {@code NoteController}'s create/update do - every
 * upload-rejection test below is a thrown-exception test
 * ({@link InvalidVoiceUploadException} -&gt; 400,
 * {@link UnsupportedAudioFormatException} -&gt; 400,
 * {@link VoiceFileTooLargeException} -&gt; 413), not a validation-annotation
 * test.
 *
 * <p>Download/stream tests here mirror
 * {@link SharedNoteControllerTest}'s attached-
 * voice-note tests (Step 15k) almost line for line - same headers, same
 * Range handling, same {@code originalFilename}-null fallback - since both
 * controllers serve the identical {@link VoiceNoteContent} shape through the
 * identical Range logic, differing only in which service/authorization path
 * produces it (direct ownership here vs. a grant on the parent note there).
 *
 * <p>Covers: {@code POST /voice-notes} - 201 with a Location built from
 * {@code fromCurrentRequest().path("/{id}")} (distinct from
 * {@code SharedNoteController}'s hand-built {@code @Value}-based Location,
 * Step 15k - this one is a relative append, since the upload path itself
 * already sits at the right base); {@code noteId}/{@code durationSeconds}
 * both optional and both forwarded when present; the three upload-rejection
 * exceptions. {@code GET /voice-notes/{id}} - 200, and
 * {@link VoiceNoteNotFoundException} -&gt; 404. {@code GET /voice-notes} -
 * the {@code @PageableDefault} (size 20, sort {@code createdAt} DESC) and
 * the {@link PagedResponse} envelope. {@code GET /voice-notes/by-note/{id}}
 * - the plain (unpaged) list, and that the literal {@code /by-note/...}
 * segment never gets swallowed by the {@code /{voiceNoteId}} template.
 * {@code GET .../download} - headers, and the {@code originalFilename}-null
 * fallback name. {@code GET .../stream} - no {@code Range} header -&gt; 200 whole
 * resource; a {@code Range} header -&gt; 206 slice. {@code DELETE
 * /voice-notes/{id}} - 204, and the same 404.
 */
@WebMvcTest(
        controllers = VoiceNoteController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class VoiceNoteControllerTest {

    private static final String BASE_URL = "/api/v1/voice-notes";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VoiceNoteService voiceNoteService;

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

    private VoiceNoteResponse sampleVoiceNote(Long id, Long ownerId, Long noteId) {
        LocalDateTime now = LocalDateTime.now();
        return new VoiceNoteResponse(id, ownerId, noteId, "memo.m4a", "audio/mp4", 4096L, 30, now, now);
    }

    private PagedResponse<VoiceNoteResponse> emptyPage() {
        Page<VoiceNoteResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        return PagedResponse.of(List.of(), page);
    }

    private MockMultipartFile audioFile() {
        return new MockMultipartFile(
                "file", "memo.m4a", "audio/mp4", "fake-audio-bytes".getBytes(StandardCharsets.UTF_8));
    }

    // ----------------------------------------------------------------
    // POST /voice-notes
    // ----------------------------------------------------------------

    @Test
    void upload_validRequest_returns201WithLocationAndBody() throws Exception {
        authenticateAs(1L);
        when(voiceNoteService.upload(eq(1L), any(), isNull(), isNull()))
                .thenReturn(sampleVoiceNote(9L, 1L, null));

        mockMvc.perform(multipart(BASE_URL).file(audioFile()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/voice-notes/9")))
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.ownerId").value(1));
    }

    @Test
    void upload_withNoteIdAndDuration_forwardsBothToTheService() throws Exception {
        authenticateAs(1L);
        when(voiceNoteService.upload(eq(1L), any(), eq(7L), eq(30)))
                .thenReturn(sampleVoiceNote(9L, 1L, 7L));

        mockMvc.perform(multipart(BASE_URL)
                        .file(audioFile())
                        .param("noteId", "7")
                        .param("durationSeconds", "30"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noteId").value(7));

        verify(voiceNoteService).upload(eq(1L), any(), eq(7L), eq(30));
    }

    @Test
    void upload_noteIdAndDurationOmitted_forwardsNullForBoth() throws Exception {
        authenticateAs(1L);
        when(voiceNoteService.upload(eq(1L), any(), isNull(), isNull()))
                .thenReturn(sampleVoiceNote(9L, 1L, null));

        mockMvc.perform(multipart(BASE_URL).file(audioFile()))
                .andExpect(status().isCreated());

        verify(voiceNoteService).upload(eq(1L), any(), isNull(), isNull());
    }

    @Test
    void upload_missingOrEmptyFile_returns400ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(voiceNoteService.upload(eq(1L), any(), any(), any()))
                .thenThrow(new InvalidVoiceUploadException("Audio file is missing or empty."));

        mockMvc.perform(multipart(BASE_URL).file(audioFile()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Audio file is missing or empty."));
    }

    @Test
    void upload_unsupportedAudioFormat_returns400ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(voiceNoteService.upload(eq(1L), any(), any(), any()))
                .thenThrow(new UnsupportedAudioFormatException("Unsupported audio format: text/plain."));

        mockMvc.perform(multipart(BASE_URL).file(audioFile()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unsupported audio format: text/plain."));
    }

    @Test
    void upload_fileTooLarge_returns413ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(voiceNoteService.upload(eq(1L), any(), any(), any()))
                .thenThrow(new VoiceFileTooLargeException("Audio file exceeds the maximum size of 25 MB."));

        mockMvc.perform(multipart(BASE_URL).file(audioFile()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("Audio file exceeds the maximum size of 25 MB."));
    }

    // ----------------------------------------------------------------
    // GET /voice-notes/{voiceNoteId}
    // ----------------------------------------------------------------

    @Test
    void getVoiceNote_validRequest_returns200() throws Exception {
        authenticateAs(1L);
        when(voiceNoteService.getVoiceNote(1L, 9L)).thenReturn(sampleVoiceNote(9L, 1L, null));

        mockMvc.perform(get(BASE_URL + "/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void getVoiceNote_unknownOrNotOwned_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(voiceNoteService.getVoiceNote(1L, 999L))
                .thenThrow(new VoiceNoteNotFoundException("Voice note not found."));

        mockMvc.perform(get(BASE_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Voice note not found."));
    }

    // ----------------------------------------------------------------
    // GET /voice-notes
    // ----------------------------------------------------------------

    @Test
    void listVoiceNotes_noParams_usesThePageableDefaultOfSize20SortedByCreatedAtDesc() throws Exception {
        authenticateAs(1L);
        when(voiceNoteService.listVoiceNotes(eq(1L), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(voiceNoteService).listVoiceNotes(eq(1L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listVoiceNotes_returnsThePagedResponseEnvelopeFromTheService() throws Exception {
        authenticateAs(1L);
        VoiceNoteResponse row = sampleVoiceNote(9L, 1L, null);
        Page<VoiceNoteResponse> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        when(voiceNoteService.listVoiceNotes(eq(1L), any())).thenReturn(PagedResponse.of(List.of(row), page));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(9))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ----------------------------------------------------------------
    // GET /voice-notes/by-note/{noteId}
    // ----------------------------------------------------------------

    @Test
    void listForNote_validRequest_returns200WithThePlainList() throws Exception {
        authenticateAs(1L);
        when(voiceNoteService.listVoiceNotesForNote(1L, 7L))
                .thenReturn(List.of(sampleVoiceNote(9L, 1L, 7L), sampleVoiceNote(10L, 1L, 7L)));

        mockMvc.perform(get(BASE_URL + "/by-note/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].noteId").value(7));
    }

    @Test
    void listForNote_literalByNoteSegment_neverConflictsWithTheIdRoute() throws Exception {
        authenticateAs(1L);
        when(voiceNoteService.listVoiceNotesForNote(1L, 7L)).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/by-note/7"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(voiceNoteService, org.mockito.Mockito.never())
                .getVoiceNote(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    // ----------------------------------------------------------------
    // GET /voice-notes/{voiceNoteId}/download
    // ----------------------------------------------------------------

    @Test
    void download_validRequest_returns200WithHeadersAndBody() throws Exception {
        authenticateAs(1L);
        byte[] bytes = "fake-audio-bytes".getBytes(StandardCharsets.UTF_8);
        VoiceNoteContent content = new VoiceNoteContent(
                new ByteArrayResource(bytes), "audio/mp4", "memo.m4a", bytes.length);
        when(voiceNoteService.loadAudio(1L, 9L)).thenReturn(content);

        mockMvc.perform(get(BASE_URL + "/9/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/mp4"))
                .andExpect(header().longValue("Content-Length", bytes.length))
                .andExpect(header().string("Content-Disposition", containsString("memo.m4a")));
    }

    @Test
    void download_noOriginalFilename_fallsBackToVoiceNoteIdBasedName() throws Exception {
        authenticateAs(1L);
        byte[] bytes = "fake-audio-bytes".getBytes(StandardCharsets.UTF_8);
        VoiceNoteContent content = new VoiceNoteContent(
                new ByteArrayResource(bytes), "audio/mp4", null, bytes.length);
        when(voiceNoteService.loadAudio(1L, 9L)).thenReturn(content);

        mockMvc.perform(get(BASE_URL + "/9/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("voice-note-9")));
    }

    @Test
    void download_unknownOrNotOwned_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(voiceNoteService.loadAudio(1L, 999L))
                .thenThrow(new VoiceNoteNotFoundException("Voice note not found."));

        mockMvc.perform(get(BASE_URL + "/999/download"))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------
    // GET /voice-notes/{voiceNoteId}/stream
    // ----------------------------------------------------------------

    @Test
    void stream_noRangeHeader_returns200WithTheWholeResourceAndAcceptRanges() throws Exception {
        authenticateAs(1L);
        byte[] bytes = "fake-audio-bytes".getBytes(StandardCharsets.UTF_8);
        VoiceNoteContent content = new VoiceNoteContent(
                new ByteArrayResource(bytes), "audio/mp4", "memo.m4a", bytes.length);
        when(voiceNoteService.loadAudio(1L, 9L)).thenReturn(content);

        mockMvc.perform(get(BASE_URL + "/9/stream"))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Type", "audio/mp4"));
    }

    @Test
    void stream_withRangeHeader_returns206WithASlice() throws Exception {
        authenticateAs(1L);
        byte[] bytes = new byte[2048];
        VoiceNoteContent content = new VoiceNoteContent(
                new ByteArrayResource(bytes), "audio/mp4", "memo.m4a", bytes.length);
        when(voiceNoteService.loadAudio(1L, 9L)).thenReturn(content);

        mockMvc.perform(get(BASE_URL + "/9/stream").header("Range", "bytes=0-1023"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Accept-Ranges", "bytes"));
    }

    @Test
    void stream_unknownOrNotOwned_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(voiceNoteService.loadAudio(1L, 999L))
                .thenThrow(new VoiceNoteNotFoundException("Voice note not found."));

        mockMvc.perform(get(BASE_URL + "/999/stream"))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------
    // DELETE /voice-notes/{voiceNoteId}
    // ----------------------------------------------------------------

    @Test
    void deleteVoiceNote_validRequest_returns204() throws Exception {
        authenticateAs(1L);
        doNothing().when(voiceNoteService).deleteVoiceNote(1L, 9L);

        mockMvc.perform(delete(BASE_URL + "/9"))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());

        verify(voiceNoteService).deleteVoiceNote(1L, 9L);
    }

    @Test
    void deleteVoiceNote_unknownOrNotOwned_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        org.mockito.Mockito.doThrow(new VoiceNoteNotFoundException("Voice note not found."))
                .when(voiceNoteService).deleteVoiceNote(1L, 999L);

        mockMvc.perform(delete(BASE_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Voice note not found."));
    }

    @Test
    void deleteVoiceNote_usesTheAuthenticatedPrincipalAsOwnerNotAnythingElse() throws Exception {
        authenticateAs(42L);
        doNothing().when(voiceNoteService).deleteVoiceNote(42L, 9L);

        mockMvc.perform(delete(BASE_URL + "/9"))
                .andExpect(status().isNoContent());

        verify(voiceNoteService).deleteVoiceNote(42L, 9L);
    }
}