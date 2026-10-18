package com.notare.feature.user.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InvalidUserLookupException;
import com.notare.common.exception.UserAlreadyExistException;
import com.notare.common.exception.UserNotFoundException;
import com.notare.feature.user.dto.response.CurrentUserResponse;
import com.notare.feature.user.dto.response.RegistrationResponse;
import com.notare.feature.user.dto.response.UserSummaryResponse;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.user.service.UserService;
import com.notare.common.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15o of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - see {@link
 * com.notare.feature.auth.web.AuthControllerTest}'s disclaimer (Step 15a);
 * same reasoning. Run via {@code mvn test} before trusting it.
 *
 * <p>Authenticated principal is supplied the same way as
 * {@link com.notare.feature.collaboration.web.CollaborationControllerTest}
 * (Step 15d) - directly on {@code SecurityContextHolder} in the test thread,
 * with {@code addFilters = false} keeping the real
 * {@code JwtAuthenticationFilter} chain out of this slice. {@code register}
 * itself needs no principal - it's on the permit-all list, same as Step
 * 15c/15h's endpoints.
 *
 * <p>{@code register} is unusual among mutating endpoints in this codebase:
 * the controller echoes {@link RegistrationResponse#getStatusCode()} as the
 * HTTP status rather than a fixed {@code ResponseEntity.status(...)}
 * constant, exactly the pattern {@code LogoutController} uses (Step 15e) -
 * so the first test below exists to confirm the controller forwards
 * whatever the service decided (201, per the service's own
 * {@code SC_CREATED}) rather than hard-coding one.
 *
 * <p>{@code /me} deliberately carries no path variable - the controller's own
 * javadoc explains why a {@code /user/{userId}} shape was rejected - so its
 * test doesn't need the "uses the authenticated principal, not the path"
 * pattern every other controller's id-based test uses; there is no other id
 * available to confuse it with.
 *
 * <p>{@code /lookup} takes a bare {@code List<Long>}, not a validated DTO, so
 * its structural failures ({@code ids} empty, over the 100-id cap, one
 * element null) are the SERVICE's job per its own javadoc
 * ({@link InvalidUserLookupException} -&gt; 400), not bean validation - which
 * is why those are exercised here as thrown-exception tests rather than
 * as-is 400s the way {@code SyncController}'s structural checks are (Step
 * 15l).
 *
 * <p>Covers: {@code POST /user/register} - the service's status code echoed
 * (201), the notarianId/message fields, a required-field validation failure,
 * and {@link UserAlreadyExistException} -&gt; 409. {@code GET /user/me} -
 * 200 with the full profile (including email, unlike the public summary
 * shape), and {@link UserNotFoundException} -&gt; 404 for a token whose
 * account no longer exists. {@code GET /user/lookup} - {@code ids} binds
 * from repeated params, the {@link InvalidUserLookupException} the service
 * throws for an empty/over-cap batch surfaces as 400, and a bare list (no
 * paging envelope) comes back unwrapped. {@code GET /user/search} - the
 * {@code @PageableDefault} (size 20, sort {@code username}, no explicit
 * direction - ascending is Spring Data's own default, not something this
 * controller states), a missing {@code q} rejected before the service runs,
 * a blank {@code q} still reaches the service (which is where the
 * "blank means no filter" rule lives, per
 * the service's own javadoc - not this controller's concern to enforce),
 * and the {@link PagedResponse} envelope.
 */
@WebSliceSupport
@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    private static final String BASE_URL = "/api/v1/user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private com.notare.feature.user.service.AccountService accountService;

    @MockBean
    private com.notare.feature.user.service.AccountDeletionService accountDeletionService;

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

    private Map<String, String> registrationBody(String firstName, String lastName, String username,
                                                 String email, String password, String confirmPassword) {
        Map<String, String> body = new HashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("username", username);
        body.put("email", email);
        body.put("password", password);
        body.put("confirmPassword", confirmPassword);
        return body;
    }

    private Map<String, String> validRegistrationBody() {
        return registrationBody("Jane", "Doe", "janedoe", "jane@example.com", "Passw0rd", "Passw0rd");
    }

    private UserSummaryResponse sampleSummary(Long id, String username) {
        return new UserSummaryResponse(id, username, "Jane", "Doe");
    }

    private PagedResponse<UserSummaryResponse> emptyPage() {
        Page<UserSummaryResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        return PagedResponse.of(List.of(), page);
    }

    // ----------------------------------------------------------------
    // POST /user/register
    // ----------------------------------------------------------------

    @Test
    void register_validRequest_echoesTheServicesStatusCodeAndBody() throws Exception {
        when(userService.registerUser(any())).thenReturn(new RegistrationResponse(
                HttpServletResponse.SC_CREATED,
                "Account created. Please check your email to verify your account.",
                "NOT-ABCDEF"));

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRegistrationBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.message").value("Account created. Please check your email to verify your account."))
                .andExpect(jsonPath("$.notarianId").value("NOT-ABCDEF"));

        verify(userService).registerUser(any());
    }

    @Test
    void register_missingRequiredField_returns400BeforeReachingTheService() throws Exception {
        Map<String, String> body = validRegistrationBody();
        body.remove("email");

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email address is required."));

        verifyNoInteractions(userService);
    }

    @Test
    void register_weakPassword_returns400BeforeReachingTheService() throws Exception {
        Map<String, String> body = validRegistrationBody();
        body.put("password", "alllowercase1");
        body.put("confirmPassword", "alllowercase1");

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());

        verifyNoInteractions(userService);
    }

    @Test
    void register_usernameOrEmailAlreadyTaken_returns409ViaGlobalExceptionHandler() throws Exception {
        when(userService.registerUser(any()))
                .thenThrow(new UserAlreadyExistException("A user with this username is already registered"));

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRegistrationBody())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A user with this username is already registered"));
    }

    // ----------------------------------------------------------------
    // GET /user/me
    // ----------------------------------------------------------------

    @Test
    void getCurrentUser_validRequest_returns200WithTheFullProfileIncludingEmail() throws Exception {
        authenticateAs(1L);
        LocalDateTime createdAt = LocalDateTime.now();
        when(userService.getCurrentUser(1L)).thenReturn(new CurrentUserResponse(
                1L, "NOT-ABCDEF", "jdoe", "Jane", "Doe", "jdoe@example.com", createdAt));

        mockMvc.perform(get(BASE_URL + "/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.notarianId").value("NOT-ABCDEF"))
                .andExpect(jsonPath("$.username").value("jdoe"))
                .andExpect(jsonPath("$.email").value("jdoe@example.com"));
    }

    @Test
    void getCurrentUser_accountNoLongerExists_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(userService.getCurrentUser(1L)).thenThrow(new UserNotFoundException("User not found."));

        mockMvc.perform(get(BASE_URL + "/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User not found."));
    }

    @Test
    void getCurrentUser_usesTheAuthenticatedPrincipalsIdNotAnythingElse() throws Exception {
        authenticateAs(42L);
        when(userService.getCurrentUser(42L)).thenReturn(new CurrentUserResponse(
                42L, "NOT-XYZ123", "other", "Other", "User", "other@example.com", LocalDateTime.now()));

        mockMvc.perform(get(BASE_URL + "/me"))
                .andExpect(status().isOk());

        verify(userService).getCurrentUser(42L);
    }

    // ----------------------------------------------------------------
    // GET /user/lookup
    // ----------------------------------------------------------------

    @Test
    void lookupUsers_repeatedIdsParam_bindsToAList() throws Exception {
        authenticateAs(1L);
        when(userService.lookupUsers(anyCollection()))
                .thenReturn(List.of(sampleSummary(2L, "alice"), sampleSummary(3L, "bob")));

        mockMvc.perform(get(BASE_URL + "/lookup").param("ids", "2", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[1].username").value("bob"));

        ArgumentCaptor<java.util.Collection<Long>> idsCaptor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(userService).lookupUsers(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(2L, 3L);
    }

    @Test
    void lookupUsers_commaSeparatedIdsParam_bindsToTheSameList() throws Exception {
        authenticateAs(1L);
        when(userService.lookupUsers(anyCollection()))
                .thenReturn(List.of(sampleSummary(2L, "alice"), sampleSummary(3L, "bob")));

        mockMvc.perform(get(BASE_URL + "/lookup").param("ids", "2,3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void lookupUsers_missingIdsParam_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(get(BASE_URL + "/lookup"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void lookupUsers_serviceRejectsAnEmptyOrOverCapBatch_returns400ViaGlobalExceptionHandler() throws Exception {
        // The empty/over-cap check is the SERVICE's job per its own javadoc,
        // not a controller-level validation constraint.
        authenticateAs(1L);
        when(userService.lookupUsers(anyCollection()))
                .thenThrow(new InvalidUserLookupException("At most 100 ids may be looked up in one request."));

        mockMvc.perform(get(BASE_URL + "/lookup").param("ids", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("At most 100 ids may be looked up in one request."));
    }

    @Test
    void lookupUsers_unknownIdsAreSimplyAbsent_notAnErrorAndNotAPagedEnvelope() throws Exception {
        authenticateAs(1L);
        when(userService.lookupUsers(anyCollection())).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/lookup").param("ids", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    // ----------------------------------------------------------------
    // GET /user/search
    // ----------------------------------------------------------------

    @Test
    void searchUsers_missingQueryParam_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(get(BASE_URL + "/search"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void searchUsers_noPagingParams_usesThePageableDefaultOfSize20SortedByUsername() throws Exception {
        authenticateAs(1L);
        when(userService.searchUsers(eq(1L), eq("ali"), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL + "/search").param("q", "ali"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).searchUsers(eq(1L), eq("ali"), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort.Order order = pageable.getSort().getOrderFor("username");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void searchUsers_explicitPagingParams_overridesTheDefault() throws Exception {
        authenticateAs(1L);
        when(userService.searchUsers(eq(1L), eq("ali"), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL + "/search")
                        .param("q", "ali")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).searchUsers(eq(1L), eq("ali"), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
    }

    @Test
    void searchUsers_blankQuery_stillReachesTheService_privacyRuleLivesThereNotHere() throws Exception {
        authenticateAs(1L);
        when(userService.searchUsers(eq(1L), eq(""), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL + "/search").param("q", ""))
                .andExpect(status().isOk());

        verify(userService).searchUsers(eq(1L), eq(""), any());
    }

    @Test
    void searchUsers_returnsThePagedResponseEnvelopeFromTheService() throws Exception {
        authenticateAs(1L);
        UserSummaryResponse row = sampleSummary(2L, "alice");
        Page<UserSummaryResponse> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        when(userService.searchUsers(eq(1L), eq("ali"), any()))
                .thenReturn(PagedResponse.of(List.of(row), page));

        mockMvc.perform(get(BASE_URL + "/search").param("q", "ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].username").value("alice"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void searchUsers_isKeyedOffTheAuthenticatedPrincipalNotAnythingElse() throws Exception {
        authenticateAs(77L);
        when(userService.searchUsers(eq(77L), eq("ali"), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL + "/search").param("q", "ali"))
                .andExpect(status().isOk());

        verify(userService).searchUsers(eq(77L), eq("ali"), any());
    }
}