package com.notare.feature.share.web;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InvalidNoteShareException;
import com.notare.common.exception.NoteAlreadySharedException;
import com.notare.common.exception.NoteNotFoundException;
import com.notare.common.exception.NoteShareNotFoundException;
import com.notare.feature.share.dto.response.NoteShareResponse;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.service.NoteShareService;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.security.JwtAuthenticationFilter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15j of the coverage roadmap.
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
 * <p>{@link NoteShareController} is nested under {@code /notes/{noteId}},
 * so every test here supplies BOTH the authenticated owner and a note id
 * path segment - unlike Step 15d's flat {@code /collaborations}, there is a
 * second, independent id in play, and one test below
 * ({@code shareNote_usesTheAuthenticatedPrincipalAsOwner}) exists
 * specifically to confirm the OWNER always comes from the principal, never
 * from anywhere in the request, while the note id legitimately does come
 * from the path.
 *
 * <p>Covers: {@code POST /notes/{noteId}/shares} - 201 with a Location
 * pointing at {@code .../shares/{sharedWithUserId}} (not a share id - the
 * controller's own javadoc on why grants are addressed by recipient); a
 * missing {@code sharedWithUserId} or {@code permission} rejected by
 * validation; an unrecognised permission string rejected at deserialization
 * (no custom handler needed - Spring MVC's enum-binding 400 is what this
 * asserts); and the three service failure modes named in the controller's
 * own javadoc - {@link InvalidNoteShareException} -&gt; 400,
 * {@link NoteNotFoundException} -&gt; 404, {@link NoteAlreadySharedException}
 * -&gt; 409. {@code GET .../shares} - the {@code @PageableDefault} (size 20,
 * sort {@code createdAt} DESC), an explicit override, the
 * {@link PagedResponse} envelope, and {@link NoteNotFoundException} -&gt; 404
 * for a note the caller doesn't own (an owner-scoped 404, never an empty
 * page - per the controller's own javadoc). {@code PUT
 * .../shares/{sharedWithUserId}} - happy path, missing {@code permission}
 * rejected by validation, and {@link NoteShareNotFoundException} -&gt; 404
 * for a grant that doesn't exist. {@code DELETE
 * .../shares/{sharedWithUserId}} - 204, delegating with the authenticated
 * owner's id (never anything else), and the same 404 for an unknown grant.
 */
@WebMvcTest(
        controllers = NoteShareController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class NoteShareControllerTest {

    private static final String SHARES_URL = "/api/v1/notes/7/shares";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NoteShareService noteShareService;

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

    private NoteShareResponse sampleShare(Long id, Long ownerId, Long sharedWithUserId, SharePermission permission) {
        LocalDateTime now = LocalDateTime.now();
        return new NoteShareResponse(id, 7L, ownerId, sharedWithUserId, permission, now, now);
    }

    private PagedResponse<NoteShareResponse> emptyPage() {
        Page<NoteShareResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        return PagedResponse.of(List.of(), page);
    }

    // ----------------------------------------------------------------
    // POST /notes/{noteId}/shares
    // ----------------------------------------------------------------

    @Test
    void shareNote_validRequest_returns201WithLocationHeaderAndBody() throws Exception {
        authenticateAs(1L);
        when(noteShareService.shareNote(eq(1L), eq(7L), any()))
                .thenReturn(sampleShare(5L, 1L, 2L, SharePermission.READ));

        mockMvc.perform(post(SHARES_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(shareBody(2L, "READ"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/notes/7/shares/2")))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.noteId").value(7))
                .andExpect(jsonPath("$.ownerId").value(1))
                .andExpect(jsonPath("$.sharedWithUserId").value(2))
                .andExpect(jsonPath("$.permission").value("READ"));
    }

    @Test
    void shareNote_locationPointsAtTheRecipientIdNotAShareId() throws Exception {
        // The controller's own javadoc: grants are addressed by recipient,
        // not by an opaque share id - the Location must reflect that even
        // though the response also carries its own numeric `id`.
        authenticateAs(1L);
        when(noteShareService.shareNote(eq(1L), eq(7L), any()))
                .thenReturn(sampleShare(999L, 1L, 2L, SharePermission.READ));

        mockMvc.perform(post(SHARES_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(shareBody(2L, "READ"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/shares/2")));
    }

    @Test
    void shareNote_usesTheAuthenticatedPrincipalAsOwner_andTheNoteIdFromThePath() throws Exception {
        authenticateAs(42L);
        when(noteShareService.shareNote(eq(42L), eq(7L), any()))
                .thenReturn(sampleShare(9L, 42L, 2L, SharePermission.READ));

        mockMvc.perform(post(SHARES_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(shareBody(2L, "READ"))))
                .andExpect(status().isCreated());

        verify(noteShareService).shareNote(eq(42L), eq(7L), any());
    }

    @Test
    void shareNote_missingSharedWithUserId_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        Map<String, Object> body = new HashMap<>();
        body.put("permission", "READ");

        mockMvc.perform(post(SHARES_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sharedWithUserId").value("sharedWithUserId is required."));

        org.mockito.Mockito.verifyNoInteractions(noteShareService);
    }

    @Test
    void shareNote_missingPermission_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        Map<String, Object> body = new HashMap<>();
        body.put("sharedWithUserId", 2L);

        mockMvc.perform(post(SHARES_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.permission").value("permission is required (READ or WRITE)."));

        org.mockito.Mockito.verifyNoInteractions(noteShareService);
    }

    @Test
    void shareNote_unrecognisedPermissionValue_returns400AtDeserializationBeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post(SHARES_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(shareBody(2L, "ADMIN"))))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(noteShareService);
    }

    @Test
    void shareNote_sharingWithYourselfOrANonCollaborator_returns400ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(noteShareService.shareNote(eq(1L), eq(7L), any()))
                .thenThrow(new InvalidNoteShareException("You can only share a note with an accepted collaborator."));

        mockMvc.perform(post(SHARES_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(shareBody(1L, "READ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You can only share a note with an accepted collaborator."));
    }

    @Test
    void shareNote_notYourNote_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(noteShareService.shareNote(eq(1L), eq(7L), any()))
                .thenThrow(new NoteNotFoundException("Note not found."));

        mockMvc.perform(post(SHARES_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(shareBody(2L, "READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Note not found."));
    }

    @Test
    void shareNote_alreadyShared_returns409ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(noteShareService.shareNote(eq(1L), eq(7L), any()))
                .thenThrow(new NoteAlreadySharedException("This note is already shared with that user."));

        mockMvc.perform(post(SHARES_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(shareBody(2L, "READ"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This note is already shared with that user."));
    }

    // ----------------------------------------------------------------
    // GET /notes/{noteId}/shares
    // ----------------------------------------------------------------

    @Test
    void listShares_noParams_usesThePageableDefaultOfSize20SortedByCreatedAtDesc() throws Exception {
        authenticateAs(1L);
        when(noteShareService.listShares(eq(1L), eq(7L), any())).thenReturn(emptyPage());

        mockMvc.perform(get(SHARES_URL))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(noteShareService).listShares(eq(1L), eq(7L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listShares_explicitPagingParams_overridesTheDefault() throws Exception {
        authenticateAs(1L);
        when(noteShareService.listShares(eq(1L), eq(7L), any())).thenReturn(emptyPage());

        mockMvc.perform(get(SHARES_URL)
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(noteShareService).listShares(eq(1L), eq(7L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        Sort.Order order = pageable.getSort().getOrderFor("id");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void listShares_returnsThePagedResponseEnvelopeFromTheService() throws Exception {
        authenticateAs(1L);
        NoteShareResponse row = sampleShare(5L, 1L, 2L, SharePermission.WRITE);
        Page<NoteShareResponse> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        when(noteShareService.listShares(eq(1L), eq(7L), any()))
                .thenReturn(PagedResponse.of(List.of(row), page));

        mockMvc.perform(get(SHARES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sharedWithUserId").value(2))
                .andExpect(jsonPath("$.content[0].permission").value("WRITE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listShares_notYourNote_returns404NotAnEmptyPage() throws Exception {
        authenticateAs(1L);
        when(noteShareService.listShares(eq(1L), eq(7L), any()))
                .thenThrow(new NoteNotFoundException("Note not found."));

        mockMvc.perform(get(SHARES_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Note not found."));
    }

    // ----------------------------------------------------------------
    // PUT /notes/{noteId}/shares/{sharedWithUserId}
    // ----------------------------------------------------------------

    @Test
    void updatePermission_validRequest_returns200WithTheUpdatedGrant() throws Exception {
        authenticateAs(1L);
        when(noteShareService.updatePermission(eq(1L), eq(7L), eq(2L), any()))
                .thenReturn(sampleShare(5L, 1L, 2L, SharePermission.WRITE));

        mockMvc.perform(put(SHARES_URL + "/2")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(permissionBody("WRITE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permission").value("WRITE"));

        verify(noteShareService).updatePermission(eq(1L), eq(7L), eq(2L), any());
    }

    @Test
    void updatePermission_missingPermission_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(put(SHARES_URL + "/2")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.permission").value("permission is required (READ or WRITE)."));

        org.mockito.Mockito.verifyNoInteractions(noteShareService);
    }

    @Test
    void updatePermission_noSuchGrant_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(noteShareService.updatePermission(eq(1L), eq(7L), eq(999L), any()))
                .thenThrow(new NoteShareNotFoundException("Share not found."));

        mockMvc.perform(put(SHARES_URL + "/999")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(permissionBody("WRITE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Share not found."));
    }

    // ----------------------------------------------------------------
    // DELETE /notes/{noteId}/shares/{sharedWithUserId}
    // ----------------------------------------------------------------

    @Test
    void revoke_validRequest_returns204AndDelegatesWithTheOwnersId() throws Exception {
        authenticateAs(1L);
        doNothing().when(noteShareService).revoke(1L, 7L, 2L);

        mockMvc.perform(delete(SHARES_URL + "/2"))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());

        verify(noteShareService).revoke(1L, 7L, 2L);
    }

    @Test
    void revoke_noSuchGrant_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        org.mockito.Mockito.doThrow(new NoteShareNotFoundException("Share not found."))
                .when(noteShareService).revoke(1L, 7L, 999L);

        mockMvc.perform(delete(SHARES_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Share not found."));
    }

    @Test
    void revoke_usesTheAuthenticatedPrincipalAsOwnerNotAnythingElse() throws Exception {
        authenticateAs(42L);
        doNothing().when(noteShareService).revoke(42L, 7L, 2L);

        mockMvc.perform(delete(SHARES_URL + "/2"))
                .andExpect(status().isNoContent());

        verify(noteShareService).revoke(42L, 7L, 2L);
    }

    private Map<String, Object> shareBody(Long sharedWithUserId, String permission) {
        Map<String, Object> body = new HashMap<>();
        body.put("sharedWithUserId", sharedWithUserId);
        body.put("permission", permission);
        return body;
    }

    private Map<String, String> permissionBody(String permission) {
        Map<String, String> body = new HashMap<>();
        body.put("permission", permission);
        return body;
    }
}