package com.notare.feature.voiceshare.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InsufficientVoiceNotePermissionException;
import com.notare.common.exception.InvalidVoiceShareException;
import com.notare.common.exception.VoiceAlreadySharedException;
import com.notare.common.exception.VoiceNoteNotFoundException;
import com.notare.common.exception.VoiceShareNotFoundException;
import com.notare.feature.collaboration.web.CollaborationControllerTest;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.voiceshare.dto.response.VoiceShareResponse;
import com.notare.feature.voiceshare.service.VoiceShareService;
import com.notare.common.security.JwtAuthenticationFilter;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15q of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - see
 * {@link AuthControllerTest}'s disclaimer
 * (Step 15a); same reasoning, same {@code @MockBean} choice for Boot 3.2.5.
 * Run via {@code mvn test} before trusting it.
 *
 * <p>{@code @AuthenticationPrincipal} is supplied the same way as
 * {@link CollaborationControllerTest}
 * (Step 15d): {@code addFilters = false} keeps the real JWT filter chain
 * out, and {@link SecurityContextHolder} is set directly, in-thread, before
 * each request, then cleared in {@code @AfterEach}.
 *
 * <p>{@link VoiceShareController} takes {@code voiceNoteId} from the path on
 * every method (it's nested under {@code /voice-notes/{voiceNoteId}/shares}),
 * so each test asserts that path segment reaches the service unchanged
 * alongside the authenticated owner id - the two together are what scope
 * every operation to "my voice note".
 *
 * <p>Covers, per the controller's own javadoc on failure modes: sharing
 * -&gt; 201 with Location, self-share/non-collaborator -&gt;
 * {@link InvalidVoiceShareException} (400), not-your-voice-note -&gt;
 * {@link VoiceNoteNotFoundException} (404), already-shared -&gt;
 * {@link VoiceAlreadySharedException} (409); listing shares with the
 * {@code @PageableDefault} (size 20, sort createdAt DESC); updating a
 * permission, including the not-found grant case
 * ({@link VoiceShareNotFoundException}); and revoke -&gt; 204. A
 * valid {@code permission} update round-trips its enum name, and an
 * unrecognised value / missing permission is rejected by validation before
 * the service runs.
 */
@WebMvcTest(
        controllers = VoiceShareController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
public class VoiceShareControllerTest {

    private static final long VOICE_NOTE_ID = 10L;
    private static final String BASE_URL = "/api/v1/voice-notes/" + VOICE_NOTE_ID + "/shares";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VoiceShareService voiceShareService;

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

    private VoiceShareResponse sampleResponse(Long id, Long voiceNoteId, Long ownerId,
                                              Long sharedWithUserId, SharePermission permission) {
        LocalDateTime now = LocalDateTime.now();
        return new VoiceShareResponse(id, voiceNoteId, ownerId, sharedWithUserId, permission, now, now);
    }

    // ----------------------------------------------------------------
    // POST /voice-notes/{voiceNoteId}/shares (shareVoiceNote)
    // ----------------------------------------------------------------

    @Test
    void shareVoiceNote_validRequest_returns201WithLocationHeaderAndBody() throws Exception {
        authenticateAs(1L);
        when(voiceShareService.shareVoiceNote(eq(1L), eq(VOICE_NOTE_ID), any()))
                .thenReturn(sampleResponse(7L, VOICE_NOTE_ID, 1L, 2L, SharePermission.READ));

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(shareBody(2L, "READ"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/shares/2")))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.voiceNoteId").value(VOICE_NOTE_ID))
                .andExpect(jsonPath("$.ownerId").value(1))
                .andExpect(jsonPath("$.sharedWithUserId").value(2))
                .andExpect(jsonPath("$.permission").value("READ"));
    }

    @Test
    void shareVoiceNote_passesTheVoiceNoteIdFromThePathAndOwnerFromThePrincipal() throws Exception {
        authenticateAs(99L);
        when(voiceShareService.shareVoiceNote(eq(99L), eq(VOICE_NOTE_ID), any()))
                .thenReturn(sampleResponse(1L, VOICE_NOTE_ID, 99L, 2L, SharePermission.WRITE));

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(shareBody(2L, "WRITE"))))
                .andExpect(status().isCreated());

        verify(voiceShareService).shareVoiceNote(eq(99L), eq(VOICE_NOTE_ID), any());
    }

    @Test
    void shareVoiceNote_missingSharedWithUserId_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content("{\"permission\":\"READ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sharedWithUserId").value("sharedWithUserId is required."));

        verifyNoInteractions(voiceShareService);
    }

    @Test
    void shareVoiceNote_missingPermission_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content("{\"sharedWithUserId\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.permission").value("permission is required (READ or WRITE)."));

        verifyNoInteractions(voiceShareService);
    }

    @Test
    void shareVoiceNote_unrecognisedPermissionValue_returns400() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content("{\"sharedWithUserId\":2,\"permission\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(voiceShareService);
    }

    @Test
    void shareVoiceNote_withYourself_returns400ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(voiceShareService.shareVoiceNote(eq(1L), eq(VOICE_NOTE_ID), any()))
                .thenThrow(new InvalidVoiceShareException("You cannot share a voice note with yourself."));

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(shareBody(1L, "READ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You cannot share a voice note with yourself."));
    }

    @Test
    void shareVoiceNote_notYourVoiceNote_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(voiceShareService.shareVoiceNote(eq(1L), eq(VOICE_NOTE_ID), any()))
                .thenThrow(new VoiceNoteNotFoundException("Voice note not found."));

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(shareBody(2L, "READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Voice note not found."));
    }

    @Test
    void shareVoiceNote_alreadyShared_returns409ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(voiceShareService.shareVoiceNote(eq(1L), eq(VOICE_NOTE_ID), any()))
                .thenThrow(new VoiceAlreadySharedException("This voice note is already shared with that user."));

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(shareBody(2L, "READ"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This voice note is already shared with that user."));
    }

    // ----------------------------------------------------------------
    // GET /voice-notes/{voiceNoteId}/shares (listShares)
    // ----------------------------------------------------------------

    @Test
    void listShares_noPagingParams_usesThePageableDefaultOfSize20SortedByCreatedAtDesc() throws Exception {
        authenticateAs(1L);
        when(voiceShareService.listShares(eq(1L), eq(VOICE_NOTE_ID), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(voiceShareService).listShares(eq(1L), eq(VOICE_NOTE_ID), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listShares_notYourVoiceNote_returns404NotAnEmptyPage() throws Exception {
        // The controller's own javadoc: "someone else's voice note is a 404,
        // not an empty page" - confirming the service's exception, not an
        // empty PagedResponse, is what a non-owner actually gets back.
        authenticateAs(2L);
        when(voiceShareService.listShares(eq(2L), eq(VOICE_NOTE_ID), any()))
                .thenThrow(new VoiceNoteNotFoundException("Voice note not found."));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Voice note not found."));
    }

    @Test
    void listShares_returnsThePagedResponseEnvelopeFromTheService() throws Exception {
        authenticateAs(1L);
        VoiceShareResponse row = sampleResponse(7L, VOICE_NOTE_ID, 1L, 2L, SharePermission.READ);
        Page<VoiceShareResponse> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        when(voiceShareService.listShares(eq(1L), eq(VOICE_NOTE_ID), any()))
                .thenReturn(PagedResponse.of(List.of(row), page));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sharedWithUserId").value(2))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ----------------------------------------------------------------
    // PUT /voice-notes/{voiceNoteId}/shares/{sharedWithUserId} (updatePermission)
    // ----------------------------------------------------------------

    @Test
    void updatePermission_validRequest_returns200WithTheUpdatedGrant() throws Exception {
        authenticateAs(1L);
        when(voiceShareService.updatePermission(eq(1L), eq(VOICE_NOTE_ID), eq(2L), any()))
                .thenReturn(sampleResponse(7L, VOICE_NOTE_ID, 1L, 2L, SharePermission.WRITE));

        mockMvc.perform(put(BASE_URL + "/2")
                        .contentType("application/json")
                        .content("{\"permission\":\"WRITE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permission").value("WRITE"));
    }

    @Test
    void updatePermission_missingPermission_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(put(BASE_URL + "/2")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.permission").value("permission is required (READ or WRITE)."));

        verifyNoInteractions(voiceShareService);
    }

    @Test
    void updatePermission_noSuchGrant_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(voiceShareService.updatePermission(eq(1L), eq(VOICE_NOTE_ID), eq(2L), any()))
                .thenThrow(new VoiceShareNotFoundException("Voice share not found."));

        mockMvc.perform(put(BASE_URL + "/2")
                        .contentType("application/json")
                        .content("{\"permission\":\"WRITE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Voice share not found."));
    }

    // ----------------------------------------------------------------
    // DELETE /voice-notes/{voiceNoteId}/shares/{sharedWithUserId} (revoke)
    // ----------------------------------------------------------------

    @Test
    void revoke_validRequest_returns204AndDelegatesWithOwnerVoiceNoteAndRecipientIds() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(delete(BASE_URL + "/2"))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());

        verify(voiceShareService).revoke(1L, VOICE_NOTE_ID, 2L);
    }

    @Test
    void revoke_noSuchGrant_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        doThrow(new VoiceShareNotFoundException("Voice share not found."))
                .when(voiceShareService).revoke(1L, VOICE_NOTE_ID, 2L);

        mockMvc.perform(delete(BASE_URL + "/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Voice share not found."));
    }

    /**
     * Not one of this controller's own documented failure modes (its javadoc
     * lists self-share/non-collaborator/not-your-note/already-shared only),
     * but {@code InsufficientVoiceNotePermissionException} is on the shared
     * exception surface every voice-note endpoint sits behind, so this
     * confirms the wiring holds even for a case this specific controller
     * doesn't call out.
     */
    @Test
    void revoke_ifThePermissionCheckEverFailedHere_itWouldStillMapToA403() throws Exception {
        authenticateAs(1L);
        doThrow(new InsufficientVoiceNotePermissionException("You do not have write access to this voice note."))
                .when(voiceShareService).revoke(1L, VOICE_NOTE_ID, 2L);

        mockMvc.perform(delete(BASE_URL + "/2"))
                .andExpect(status().isForbidden());
    }

    private PagedResponse<VoiceShareResponse> emptyPage() {
        Page<VoiceShareResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        return PagedResponse.of(List.of(), page);
    }

    private Map<String, Object> shareBody(Long sharedWithUserId, String permission) {
        Map<String, Object> body = new HashMap<>();
        body.put("sharedWithUserId", sharedWithUserId);
        body.put("permission", permission);
        return body;
    }
}