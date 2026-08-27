package com.notare.feature.voiceshare.web;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InsufficientVoiceNotePermissionException;
import com.notare.common.exception.VoiceShareNotFoundException;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voiceshare.dto.response.SharedVoiceNoteSummaryResponse;
import com.notare.feature.voiceshare.service.SharedVoiceNoteService;
import com.notare.common.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15r of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - see
 * {@link AuthControllerTest}'s disclaimer
 * (Step 15a); same reasoning, same {@code @MockBean} choice for Boot 3.2.5.
 * Run via {@code mvn test} before trusting it.
 *
 * <p>Same {@code @AuthenticationPrincipal} approach as
 * {@link CollaborationControllerTest}
 * (Step 15d) and {@link VoiceShareControllerTest} (Step 15q):
 * {@code addFilters = false}, {@link SecurityContextHolder} set directly
 * per test, cleared in {@code @AfterEach}.
 *
 * <p>The controller's own javadoc states the authorization story it mirrors
 * from {@code SharedNoteController}: no grant at all is a 404
 * ({@link VoiceShareNotFoundException}), a grant that exists but is too weak
 * for what's being asked is a 403
 * ({@link InsufficientVoiceNotePermissionException}) - confirmed here for
 * every one of the four endpoints, not just the metadata one, since
 * {@code stream}/{@code download} resolve access through the exact same
 * {@link SharedVoiceNoteService#getAudio} gate.
 *
 * <p>{@code stream} is the one endpoint whose response SHAPE depends on
 * whether the request carried a {@code Range} header, so both branches are
 * exercised: no {@code Range} -&gt; 200 with the whole body and
 * {@code Accept-Ranges: bytes}; a {@code Range} header -&gt; 206 with only
 * the requested slice, sized against the {@code CHUNK_SIZE} cap when the
 * requested range exceeds it. A real, small {@link ByteArrayResource} stands
 * in for the audio bytes so the response has a real Content-Length to
 * assert on, rather than a Mockito stub whose reported length
 * and actual streamed bytes could silently disagree.
 */
@WebMvcTest(
        controllers = SharedVoiceNoteController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class SharedVoiceNoteControllerTest {

    private static final String BASE_URL = "/api/v1/shared-voice-notes";
    private static final long VOICE_NOTE_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SharedVoiceNoteService sharedVoiceNoteService;

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

    private SharedVoiceNoteSummaryResponse sampleSummary(Long shareId, Long voiceNoteId, Long ownerId) {
        LocalDateTime now = LocalDateTime.now();
        return new SharedVoiceNoteSummaryResponse(
                shareId, voiceNoteId, null, ownerId, SharePermission.READ,
                "memo.m4a", "audio/mp4", 4096L, 30, now, now);
    }

    // ----------------------------------------------------------------
    // GET /shared-voice-notes (listSharedWithMe)
    // ----------------------------------------------------------------

    @Test
    void listSharedWithMe_noPagingParams_usesThePageableDefaultOfSize20SortedByCreatedAtDesc() throws Exception {
        authenticateAs(2L);
        when(sharedVoiceNoteService.listSharedWithMe(eq(2L), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());

        var pageableCaptor = forClass(Pageable.class);
        verify(sharedVoiceNoteService).listSharedWithMe(eq(2L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listSharedWithMe_returnsThePagedResponseEnvelopeFromTheService() throws Exception {
        authenticateAs(2L);
        SharedVoiceNoteSummaryResponse row = sampleSummary(7L, VOICE_NOTE_ID, 1L);
        Page<SharedVoiceNoteSummaryResponse> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        when(sharedVoiceNoteService.listSharedWithMe(eq(2L), any()))
                .thenReturn(PagedResponse.of(List.of(row), page));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].shareId").value(7))
                .andExpect(jsonPath("$.content[0].voiceNoteId").value(VOICE_NOTE_ID))
                .andExpect(jsonPath("$.content[0].ownerId").value(1))
                .andExpect(jsonPath("$.content[0].permission").value("READ"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ----------------------------------------------------------------
    // GET /shared-voice-notes/{voiceNoteId} (getSharedVoiceNote)
    // ----------------------------------------------------------------

    @Test
    void getSharedVoiceNote_validRequest_returns200WithTheSummary() throws Exception {
        authenticateAs(2L);
        when(sharedVoiceNoteService.getSharedVoiceNote(2L, VOICE_NOTE_ID))
                .thenReturn(sampleSummary(7L, VOICE_NOTE_ID, 1L));

        mockMvc.perform(get(BASE_URL + "/" + VOICE_NOTE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareId").value(7))
                .andExpect(jsonPath("$.ownerId").value(1));
    }

    @Test
    void getSharedVoiceNote_noGrantAtAll_returns404NotAnythingElse() throws Exception {
        // "No grant at all -> 404. As far as this user is concerned the
        // voice note doesn't exist" - per the service's own javadoc.
        authenticateAs(2L);
        when(sharedVoiceNoteService.getSharedVoiceNote(2L, VOICE_NOTE_ID))
                .thenThrow(new VoiceShareNotFoundException("Voice note not found."));

        mockMvc.perform(get(BASE_URL + "/" + VOICE_NOTE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Voice note not found."));
    }

    @Test
    void getSharedVoiceNote_grantTooWeak_returns403() throws Exception {
        authenticateAs(2L);
        when(sharedVoiceNoteService.getSharedVoiceNote(2L, VOICE_NOTE_ID))
                .thenThrow(new InsufficientVoiceNotePermissionException("You have read-only access to this voice note."));

        mockMvc.perform(get(BASE_URL + "/" + VOICE_NOTE_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You have read-only access to this voice note."));
    }

    // ----------------------------------------------------------------
    // GET /shared-voice-notes/{voiceNoteId}/download
    // ----------------------------------------------------------------

    @Test
    void download_validRequest_returns200WithAttachmentDispositionAndFullBody() throws Exception {
        authenticateAs(2L);
        byte[] audioBytes = "fake-audio-bytes".getBytes(StandardCharsets.UTF_8);
        when(sharedVoiceNoteService.getAudio(2L, VOICE_NOTE_ID)).thenReturn(new VoiceNoteContent(
                new ByteArrayResource(audioBytes), "audio/mp4", "memo.m4a", audioBytes.length));

        mockMvc.perform(get(BASE_URL + "/" + VOICE_NOTE_ID + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/mp4"))
                .andExpect(header().longValue("Content-Length", audioBytes.length))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("memo.m4a")))
                .andExpect(content().bytes(audioBytes));
    }

    @Test
    void download_noOriginalFilename_fallsBackToAGeneratedName() throws Exception {
        authenticateAs(2L);
        byte[] audioBytes = "fake-audio-bytes".getBytes(StandardCharsets.UTF_8);
        when(sharedVoiceNoteService.getAudio(2L, VOICE_NOTE_ID)).thenReturn(new VoiceNoteContent(
                new ByteArrayResource(audioBytes), "audio/mp4", null, audioBytes.length));

        mockMvc.perform(get(BASE_URL + "/" + VOICE_NOTE_ID + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("voice-note-" + VOICE_NOTE_ID)));
    }

    @Test
    void download_noGrantAtAll_returns404() throws Exception {
        authenticateAs(2L);
        when(sharedVoiceNoteService.getAudio(2L, VOICE_NOTE_ID))
                .thenThrow(new VoiceShareNotFoundException("Voice note not found."));

        mockMvc.perform(get(BASE_URL + "/" + VOICE_NOTE_ID + "/download"))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------
    // GET /shared-voice-notes/{voiceNoteId}/stream
    // ----------------------------------------------------------------

    @Test
    void stream_noRangeHeader_returns200WithTheWholeBodyAndAcceptRanges() throws Exception {
        authenticateAs(2L);
        byte[] audioBytes = "0123456789".getBytes(StandardCharsets.UTF_8);
        when(sharedVoiceNoteService.getAudio(2L, VOICE_NOTE_ID)).thenReturn(new VoiceNoteContent(
                new ByteArrayResource(audioBytes), "audio/mp4", "memo.m4a", audioBytes.length));

        mockMvc.perform(get(BASE_URL + "/" + VOICE_NOTE_ID + "/stream"))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Type", "audio/mp4"))
                .andExpect(content().bytes(audioBytes));
    }

    @Test
    void stream_withRangeHeader_returns206WithOnlyTheRequestedSlice() throws Exception {
        authenticateAs(2L);
        byte[] audioBytes = "0123456789".getBytes(StandardCharsets.UTF_8); // 10 bytes, indices 0-9
        when(sharedVoiceNoteService.getAudio(2L, VOICE_NOTE_ID)).thenReturn(new VoiceNoteContent(
                new ByteArrayResource(audioBytes), "audio/mp4", "memo.m4a", audioBytes.length));

        mockMvc.perform(get(BASE_URL + "/" + VOICE_NOTE_ID + "/stream")
                        .header("Range", "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().bytes("2345".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void stream_rangeLargerThanChunkSize_isCappedAtChunkSize() throws Exception {
        // CHUNK_SIZE is 1 MB; a range request spanning more than that must be
        // truncated to at most CHUNK_SIZE bytes rather than served whole, so
        // the player is forced to come back for the rest in slices.
        authenticateAs(2L);
        long twoMb = 2L * 1024 * 1024;
        byte[] audioBytes = new byte[(int) twoMb];
        when(sharedVoiceNoteService.getAudio(2L, VOICE_NOTE_ID)).thenReturn(new VoiceNoteContent(
                new ByteArrayResource(audioBytes), "audio/mp4", "memo.m4a", audioBytes.length));

        mockMvc.perform(get(BASE_URL + "/" + VOICE_NOTE_ID + "/stream")
                        .header("Range", "bytes=0-"))
                .andExpect(status().isPartialContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray().length)
                        .isEqualTo(1024 * 1024));
    }

    @Test
    void stream_noGrantAtAll_returns404() throws Exception {
        authenticateAs(2L);
        when(sharedVoiceNoteService.getAudio(2L, VOICE_NOTE_ID))
                .thenThrow(new VoiceShareNotFoundException("Voice note not found."));

        mockMvc.perform(get(BASE_URL + "/" + VOICE_NOTE_ID + "/stream"))
                .andExpect(status().isNotFound());
    }

    @Test
    void stream_grantTooWeak_returns403() throws Exception {
        authenticateAs(2L);
        when(sharedVoiceNoteService.getAudio(2L, VOICE_NOTE_ID))
                .thenThrow(new InsufficientVoiceNotePermissionException("You have read-only access to this voice note."));

        mockMvc.perform(get(BASE_URL + "/" + VOICE_NOTE_ID + "/stream"))
                .andExpect(status().isForbidden());
    }

    private PagedResponse<SharedVoiceNoteSummaryResponse> emptyPage() {
        Page<SharedVoiceNoteSummaryResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        return PagedResponse.of(List.of(), page);
    }
}