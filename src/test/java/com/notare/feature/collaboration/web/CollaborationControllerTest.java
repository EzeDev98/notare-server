package com.notare.feature.collaboration.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.exception.CollaborationAlreadyExistsException;
import com.notare.common.exception.CollaborationNotFoundException;
import com.notare.common.exception.InvalidCollaborationException;
import com.notare.common.exception.UserNotFoundException;
import com.notare.common.dto.PagedResponse;
import com.notare.feature.collaboration.dto.response.CollaborationResponse;
import com.notare.feature.collaboration.enums.CollaborationStatus;
import com.notare.feature.collaboration.service.CollaborationService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15d of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - see {@link
 * com.notare.feature.auth.web.AuthControllerTest}'s disclaimer (Step 15a);
 * same reasoning. Run via {@code mvn test} before trusting it.
 *
 * <h2>How {@code @AuthenticationPrincipal} is supplied without the real
 * security filter chain</h2>
 *
 * <p>This is the first controller in the roadmap that reads
 * {@code @AuthenticationPrincipal UserDetail} - every prior slice
 * (Auth/Login/Otp) had no authenticated principal at all, because their
 * endpoints are on {@code WebSecurityConfig}'s permit-all list.
 * {@code /collaborations} is NOT on that list, but {@code addFilters =
 * false} still deliberately keeps {@code JwtAuthenticationFilter} (and its
 * three dependencies: {@code JwtService}, {@code CustomUserDetailsService},
 * {@code SessionService}) out of this slice entirely, per the same
 * reasoning as Step 15a: this class tests {@link CollaborationController}'s
 * own request mapping and response shaping, not JWT parsing or session
 * validity, which is already covered by
 * {@code JwtAuthenticationFilterTest} (Step 4b).
 *
 * <p>The replacement is to set {@link SecurityContextHolder} directly, in the
 * test's own thread, before each {@code mockMvc.perform(...)} call: MockMvc
 * runs synchronously on the calling thread, and Spring Security's
 * {@code AuthenticationPrincipalArgumentResolver} (registered by Spring
 * Boot's default security auto-configuration, active in {@code @WebMvcTest}
 * whether or not a custom {@code SecurityFilterChain} bean is imported) reads
 * the principal straight off {@code SecurityContextHolder.getContext()
 * .getAuthentication()} - no servlet filter has to run for that resolver to
 * do its job. The context is cleared in {@code @AfterEach} so one test's
 * principal never leaks into the next.
 *
 * <p>Covers: {@code POST /collaborations} - 201 with Location header and
 * body on success; a missing {@code collaboratorId} rejected by validation;
 * and the three failure modes the controller's own javadoc calls out -
 * {@link InvalidCollaborationException} -&gt; 400, {@link UserNotFoundException}
 * -&gt; 404, {@link CollaborationAlreadyExistsException} -&gt; 409.
 * {@code POST .../accept} and {@code .../reject} - 200 on success, 404 via
 * {@link CollaborationNotFoundException} when not addressed to the caller.
 * {@code DELETE /collaborations/{id}} - 204, and confirms the acting user id
 * (not the path) is what the controller sends as the owner. {@code GET
 * .../sent} and {@code .../received} - the {@code @PageableDefault} (size 20,
 * sort updatedAt DESC) is what actually reaches the service when the caller
 * supplies no paging params, an explicit page/size/sort request overrides it,
 * and the {@link PagedResponse} envelope round-trips through JSON.
 */
@WebMvcTest(controllers = CollaborationController.class)
@AutoConfigureMockMvc(addFilters = false)
class CollaborationControllerTest {

    private static final String BASE_URL = "/api/v1/collaborations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CollaborationService collaborationService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private UserDetail authenticateAs(long userId) {
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
        return userDetail;
    }

    private CollaborationResponse sampleResponse(Long id, Long ownerId, Long collaboratorId, CollaborationStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return new CollaborationResponse(id, ownerId, collaboratorId, status, now, now);
    }

    // ----------------------------------------------------------------
    // POST /collaborations (invite)
    // ----------------------------------------------------------------

    @Test
    void invite_validRequest_returns201WithLocationHeaderAndBody() throws Exception {
        authenticateAs(1L);
        when(collaborationService.invite(1L, 2L))
                .thenReturn(sampleResponse(5L, 1L, 2L, CollaborationStatus.PENDING));

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(inviteBody(2L))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/collaborations/5")))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.ownerId").value(1))
                .andExpect(jsonPath("$.collaboratorId").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void invite_usesTheAuthenticatedPrincipalAsOwnerNotAnythingFromTheBody() throws Exception {
        // CreateInvitationRequest carries only collaboratorId - the owner
        // can only ever come from the authenticated principal.
        authenticateAs(42L);
        when(collaborationService.invite(42L, 2L))
                .thenReturn(sampleResponse(9L, 42L, 2L, CollaborationStatus.PENDING));

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(inviteBody(2L))))
                .andExpect(status().isCreated());

        verify(collaborationService).invite(eq(42L), eq(2L));
    }

    @Test
    void invite_missingCollaboratorId_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.collaboratorId").value("collaboratorId is required."));

        org.mockito.Mockito.verifyNoInteractions(collaborationService);
    }

    @Test
    void invite_invitingYourself_returns400ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(collaborationService.invite(1L, 1L))
                .thenThrow(new InvalidCollaborationException("You cannot invite yourself."));

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(inviteBody(1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You cannot invite yourself."));
    }

    @Test
    void invite_unknownInvitee_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(collaborationService.invite(1L, 999L))
                .thenThrow(new UserNotFoundException("User not found."));

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(inviteBody(999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User not found."));
    }

    @Test
    void invite_alreadyInvited_returns409ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(collaborationService.invite(1L, 2L))
                .thenThrow(new CollaborationAlreadyExistsException("Collaboration already exists."));

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(inviteBody(2L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Collaboration already exists."));
    }

    // ----------------------------------------------------------------
    // POST /collaborations/{id}/accept and /reject
    // ----------------------------------------------------------------

    @Test
    void accept_validRequest_returns200WithTheUpdatedCollaboration() throws Exception {
        authenticateAs(2L);
        when(collaborationService.accept(2L, 5L))
                .thenReturn(sampleResponse(5L, 1L, 2L, CollaborationStatus.ACCEPTED));

        mockMvc.perform(post(BASE_URL + "/5/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(collaborationService).accept(2L, 5L);
    }

    @Test
    void accept_notAddressedToMe_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(999L);
        when(collaborationService.accept(999L, 5L))
                .thenThrow(new CollaborationNotFoundException("Collaboration not found."));

        mockMvc.perform(post(BASE_URL + "/5/accept"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Collaboration not found."));
    }

    @Test
    void reject_validRequest_returns200WithTheUpdatedCollaboration() throws Exception {
        authenticateAs(2L);
        when(collaborationService.reject(2L, 5L))
                .thenReturn(sampleResponse(5L, 1L, 2L, CollaborationStatus.REJECTED));

        mockMvc.perform(post(BASE_URL + "/5/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(collaborationService).reject(2L, 5L);
    }

    // ----------------------------------------------------------------
    // DELETE /collaborations/{id} (cancel)
    // ----------------------------------------------------------------

    @Test
    void cancel_validRequest_returns204AndDelegatesToTheServiceWithTheCallersId() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(delete(BASE_URL + "/5"))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());

        verify(collaborationService).cancel(1L, 5L);
    }

    @Test
    void cancel_notTheOwner_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(2L);
        doThrow(new CollaborationNotFoundException("Collaboration not found."))
                .when(collaborationService).cancel(2L, 5L);

        mockMvc.perform(delete(BASE_URL + "/5"))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------
    // GET /collaborations/sent and /received
    // ----------------------------------------------------------------

    @Test
    void listSent_noPagingParams_usesThePageableDefaultOfSize20SortedByUpdatedAtDesc() throws Exception {
        authenticateAs(1L);
        when(collaborationService.listSent(eq(1L), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL + "/sent"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(collaborationService).listSent(eq(1L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getPageNumber()).isZero();
        Sort.Order order = pageable.getSort().getOrderFor("updatedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listSent_explicitPagingParams_overridesTheDefault() throws Exception {
        authenticateAs(1L);
        when(collaborationService.listSent(eq(1L), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL + "/sent")
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(collaborationService).listSent(eq(1L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        Sort.Order order = pageable.getSort().getOrderFor("id");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void listSent_returnsThePagedResponseEnvelopeFromTheService() throws Exception {
        authenticateAs(1L);
        CollaborationResponse row = sampleResponse(5L, 1L, 2L, CollaborationStatus.PENDING);
        Page<CollaborationResponse> page = new PageImpl<>(
                List.of(row), PageRequest.of(0, 20), 1);
        when(collaborationService.listSent(eq(1L), any()))
                .thenReturn(PagedResponse.of(List.of(row), page));

        mockMvc.perform(get(BASE_URL + "/sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listReceived_noPagingParams_usesThePageableDefaultOfSize20SortedByUpdatedAtDesc() throws Exception {
        authenticateAs(2L);
        when(collaborationService.listReceived(eq(2L), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL + "/received"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(collaborationService).listReceived(eq(2L), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort.Order order = pageable.getSort().getOrderFor("updatedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listReceived_isKeyedOffTheCallerNotAQueryParam_soTwoUsersNeverShareAResult() throws Exception {
        // listReceived and listSent both take only the authenticated user's
        // id - there is no "whose invitations" parameter a caller could
        // manipulate to see someone else's.
        authenticateAs(2L);
        when(collaborationService.listReceived(anyLong(), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL + "/received"))
                .andExpect(status().isOk());

        verify(collaborationService).listReceived(eq(2L), any());
    }

    private PagedResponse<CollaborationResponse> emptyPage() {
        Page<CollaborationResponse> page = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0);
        return PagedResponse.of(List.of(), page);
    }

    private Map<String, Long> inviteBody(Long collaboratorId) {
        Map<String, Long> body = new HashMap<>();
        body.put("collaboratorId", collaboratorId);
        return body;
    }
}