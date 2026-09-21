package com.notare.feature.transcription.web;

import com.notare.common.exception.TranscriptionNotFoundException;
import com.notare.common.exception.VoiceNoteNotFoundException;
import com.notare.feature.transcription.dto.response.TranscriptionResponse;
import com.notare.feature.transcription.enums.TranscriptionStatus;
import com.notare.feature.transcription.service.TranscriptionService;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.common.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15m of the coverage roadmap.
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
 * <p>{@code submit}'s Location header is built from {@code
 * ServletUriComponentsBuilder.fromCurrentRequest()} rather than pointing at a
 * different resource - per the controller's own comment, the poll URL IS
 * this same GET, so the assertion below is that Location equals the request
 * path itself, not merely that it exists.
 *
 * <p>Both of this controller's failure modes come from a DIFFERENT module
 * than the one it lives in: {@code submit} delegates the
 * voice-note-exists-and-is-mine check to {@code VoiceNoteService} (the
 * controller's own comment on why - consulting another module's public
 * service, not its repository), so a bad {@code voiceNoteId} surfaces as
 * {@link VoiceNoteNotFoundException}, not a transcription-specific one.
 * {@code getStatus} surfaces {@link TranscriptionNotFoundException} for a
 * voice note that exists but has no submitted job. Both map to 404 via
 * {@code GlobalExceptionHandler} (Step 14a) and are indistinguishable to the
 * client at the HTTP layer - which is this controller's actual contract to
 * verify, since the two exception types come from different services
 * entirely.
 *
 * <p>Covers: {@code POST .../transcription} - 202 Accepted with a Location
 * equal to the request's own path and a PENDING body on first submission; a
 * resubmission against an already PENDING/PROCESSING/COMPLETED job still
 * returns 202 with whatever state the service hands back (idempotent, per
 * the controller's own javadoc - the controller does not special-case this,
 * so the test's job is confirming it doesn't); and
 * {@link VoiceNoteNotFoundException} -&gt; 404 for a voice note that isn't
 * the caller's. {@code GET .../transcription} - 200 with the completed
 * job's transcript/confidence/language fields, and {@link TranscriptionNotFoundException} -&gt; 404 when no
 * job has ever been submitted for that voice note.
 */
@WebMvcTest(
        controllers = TranscriptionController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class TranscriptionControllerTest {

    private static final String TRANSCRIPTION_URL = "/api/v1/voice-notes/7/transcription";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TranscriptionService transcriptionService;

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

    private TranscriptionResponse pendingTranscription(Long id, Long voiceNoteId) {
        LocalDateTime now = LocalDateTime.now();
        return new TranscriptionResponse(id, voiceNoteId, TranscriptionStatus.PENDING, null, null, null, null, now, now);
    }

    private TranscriptionResponse completedTranscription(Long id, Long voiceNoteId) {
        LocalDateTime now = LocalDateTime.now();
        return new TranscriptionResponse(
                id, voiceNoteId, TranscriptionStatus.COMPLETED,
                "hello world", 0.97, "en", null, now, now);
    }

    // ----------------------------------------------------------------
    // POST /voice-notes/{voiceNoteId}/transcription
    // ----------------------------------------------------------------

    @Test
    void submit_firstSubmission_returns202WithLocationEqualToTheRequestPathAndAPendingBody() throws Exception {
        authenticateAs(1L);
        when(transcriptionService.submit(1L, 7L)).thenReturn(pendingTranscription(3L, 7L));

        mockMvc.perform(post(TRANSCRIPTION_URL))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith(TRANSCRIPTION_URL)))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.voiceNoteId").value(7))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.transcript").doesNotExist());

        verify(transcriptionService).submit(1L, 7L);
    }

    @Test
    void submit_alreadyProcessing_isIdempotent_stillReturns202WithTheCurrentState() throws Exception {
        authenticateAs(1L);
        LocalDateTime now = LocalDateTime.now();
        TranscriptionResponse processing = new TranscriptionResponse(
                3L, 7L, TranscriptionStatus.PROCESSING, null, null, null, null, now, now);
        when(transcriptionService.submit(1L, 7L)).thenReturn(processing);

        mockMvc.perform(post(TRANSCRIPTION_URL))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void submit_unknownOrNotOwnedVoiceNote_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(transcriptionService.submit(1L, 999L))
                .thenThrow(new VoiceNoteNotFoundException("Voice note not found."));

        mockMvc.perform(post("/api/v1/voice-notes/999/transcription"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Voice note not found."));
    }

    @Test
    void submit_usesTheAuthenticatedPrincipalAsOwner() throws Exception {
        authenticateAs(42L);
        when(transcriptionService.submit(eq(42L), eq(7L))).thenReturn(pendingTranscription(3L, 7L));

        mockMvc.perform(post(TRANSCRIPTION_URL))
                .andExpect(status().isAccepted());

        verify(transcriptionService).submit(42L, 7L);
    }

    // ----------------------------------------------------------------
    // GET /voice-notes/{voiceNoteId}/transcription
    // ----------------------------------------------------------------

    @Test
    void getStatus_completedJob_returns200WithTheFullResult() throws Exception {
        authenticateAs(1L);
        when(transcriptionService.getByVoiceNote(1L, 7L)).thenReturn(completedTranscription(3L, 7L));

        mockMvc.perform(get(TRANSCRIPTION_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.transcript").value("hello world"))
                .andExpect(jsonPath("$.confidence").value(0.97))
                .andExpect(jsonPath("$.language").value("en"));
    }

    @Test
    void getStatus_noJobEverSubmitted_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(transcriptionService.getByVoiceNote(1L, 999L))
                .thenThrow(new TranscriptionNotFoundException("No transcription found for this voice note."));

        mockMvc.perform(get("/api/v1/voice-notes/999/transcription"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No transcription found for this voice note."));
    }

    @Test
    void getStatus_usesTheAuthenticatedPrincipalAsOwner() throws Exception {
        authenticateAs(42L);
        when(transcriptionService.getByVoiceNote(eq(42L), eq(7L))).thenReturn(completedTranscription(3L, 7L));

        mockMvc.perform(get(TRANSCRIPTION_URL))
                .andExpect(status().isOk());

        verify(transcriptionService).getByVoiceNote(42L, 7L);
    }
}