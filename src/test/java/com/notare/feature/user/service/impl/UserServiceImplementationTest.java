package com.notare.feature.user.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InvalidUserLookupException;
import com.notare.common.exception.NotarianIdGenerationException;
import com.notare.common.exception.PasswordMisMatchException;
import com.notare.common.exception.UserAlreadyExistException;
import com.notare.common.exception.UserNotFoundException;
import com.notare.common.util.LikeEscaper;
import com.notare.feature.user.dto.request.RegistrationRequest;
import com.notare.feature.user.dto.response.CurrentUserResponse;
import com.notare.feature.user.dto.response.RegistrationResponse;
import com.notare.feature.user.dto.response.UserSummaryResponse;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.event.UserRegisteredEvent;
import com.notare.feature.user.model.User;
import com.notare.feature.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Step 5a of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito, Spring Security ({@code PasswordEncoder}), Spring
 * context ({@code ApplicationEventPublisher}), and Spring Data
 * ({@code Page}/{@code Pageable}) are unavailable as compile-time
 * dependencies here, and {@code target/classes} only holds this project's own
 * already-compiled classes, not third-party jars. Every test below is
 * reasoned carefully from {@link UserServiceImplementation}'s source and
 * follows this codebase's existing Mockito conventions exactly, but it has
 * not actually been compiled or run. Please run it via {@code mvn test}
 * before trusting it.
 *
 * <p>Covers {@link UserServiceImplementation#registerUser}, {@code
 * getCurrentUser}, {@code lookupUsers}, {@code searchUsers}, and {@code
 * existsById} - every branch of every method on the class, including the
 * notarianId-generation retry loop and the deliberately-asymmetric privacy
 * behavior of {@code searchUsers} (blank term -> nothing, vs. note/transcript
 * search where blank means "list all").
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceImplementationTest {

    private static final int MAX_ID_GENERATION_ATTEMPTS = 10;
    private static final int MAX_USER_LOOKUP_IDS = 100;

    @Mock private NotarianIdGenerator notarianIdGenerator;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    public UserServiceImplementation service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImplementation(notarianIdGenerator, passwordEncoder, userRepository, eventPublisher);
    }

    private RegistrationRequest registrationRequest() {
        RegistrationRequest request = new RegistrationRequest();
        request.setFirstName(" John ");
        request.setLastName(" Doe ");
        request.setUsername(" johndoe ");
        request.setEmail(" JOHN@EXAMPLE.COM ");
        request.setPassword("Passw0rd!");
        request.setConfirmPassword("Passw0rd!");
        return request;
    }

    private User existingUser(String username, String email) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }

    // ---- registerUser -------------------------------------------------

    @Test
    void registerUser_throwsPasswordMisMatchException_whenPasswordsDiffer() {
        RegistrationRequest request = registrationRequest();
        request.setConfirmPassword("SomethingElse1");

        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(PasswordMisMatchException.class)
                .hasMessage("Passwords do not match");

        // Fails before any repository/generator interaction - cheapest check first.
        verifyNoInteractions(userRepository, notarianIdGenerator, eventPublisher);
    }

    @Test
    void registerUser_throwsUserAlreadyExistException_whenUsernameIsTaken() {
        RegistrationRequest request = registrationRequest();
        when(userRepository.findByUsernameOrEmail("johndoe", "john@example.com"))
                .thenReturn(Optional.of(existingUser("johndoe", "someoneelse@example.com")));

        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(UserAlreadyExistException.class)
                .hasMessage("A user with this username is already registered");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_throwsUserAlreadyExistException_whenOnlyEmailIsTaken() {
        RegistrationRequest request = registrationRequest();
        // Username differs from the request's, so the branch falls through to
        // the email-specific message.
        when(userRepository.findByUsernameOrEmail("johndoe", "john@example.com"))
                .thenReturn(Optional.of(existingUser("someoneelse", "john@example.com")));

        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(UserAlreadyExistException.class)
                .hasMessage("A user with this email is already registered");
    }

    @Test
    void registerUser_trimsAndNormalizesFields_encodesPassword_andSetsInitialStatus() {
        RegistrationRequest request = registrationRequest();
        when(userRepository.findByUsernameOrEmail("johndoe", "john@example.com")).thenReturn(Optional.empty());
        when(notarianIdGenerator.generateId()).thenReturn("BADAZOP");
        when(userRepository.existsByNotarianId("BADAZOP")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        RegistrationResponse response = service.registerUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getLastName()).isEqualTo("Doe");
        assertThat(saved.getUsername()).isEqualTo("johndoe");
        assertThat(saved.getEmail()).isEqualTo("john@example.com");
        assertThat(saved.getPassword()).isEqualTo("$2a$encoded");
        assertThat(saved.getNotarianId()).isEqualTo("BADAZOP");
        assertThat(saved.getUserStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(saved.isEmailVerified()).isFalse();

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.getMessage()).isEqualTo("Account created. Please check your email to verify your account.");
        assertThat(response.getNotarianId()).isEqualTo("BADAZOP");
    }

    @Test
    void registerUser_publishesUserRegisteredEventWithTheSavedUsersId() {
        RegistrationRequest request = registrationRequest();
        when(userRepository.findByUsernameOrEmail("johndoe", "john@example.com")).thenReturn(Optional.empty());
        when(notarianIdGenerator.generateId()).thenReturn("BADAZOP");
        when(userRepository.existsByNotarianId("BADAZOP")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(77L);
            return saved;
        });

        service.registerUser(request);

        ArgumentCaptor<UserRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(77L);
    }

    @Test
    void registerUser_retriesNotarianIdGeneration_untilACollisionFreeIdIsFound() {
        RegistrationRequest request = registrationRequest();
        when(userRepository.findByUsernameOrEmail("johndoe", "john@example.com")).thenReturn(Optional.empty());
        when(notarianIdGenerator.generateId()).thenReturn("TAKEN11", "TAKEN11", "FREEID1");
        when(userRepository.existsByNotarianId("TAKEN11")).thenReturn(true);
        when(userRepository.existsByNotarianId("FREEID1")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponse response = service.registerUser(request);

        assertThat(response.getNotarianId()).isEqualTo("FREEID1");
        verify(notarianIdGenerator, times(2)).generateId();
    }

    @Test
    void registerUser_throwsNotarianIdGenerationException_afterExhaustingAllAttempts() {
        RegistrationRequest request = registrationRequest();
        when(userRepository.findByUsernameOrEmail("johndoe", "john@example.com")).thenReturn(Optional.empty());
        when(notarianIdGenerator.generateId()).thenReturn("ALWAYSSAME");
        when(userRepository.existsByNotarianId("ALWAYSSAME")).thenReturn(true);

        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(NotarianIdGenerationException.class)
                .hasMessageContaining(String.valueOf(MAX_ID_GENERATION_ATTEMPTS));

        verify(notarianIdGenerator, times(MAX_ID_GENERATION_ATTEMPTS)).generateId();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_wrapsDataIntegrityViolationExceptionFromSave_intoUserAlreadyExistException() {
        RegistrationRequest request = registrationRequest();
        when(userRepository.findByUsernameOrEmail("johndoe", "john@example.com")).thenReturn(Optional.empty());
        when(notarianIdGenerator.generateId()).thenReturn("BADAZOP");
        when(userRepository.existsByNotarianId("BADAZOP")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        // The pre-check with findByUsernameOrEmail is best-effort, not a
        // guarantee - a concurrent registration can still slip a duplicate
        // past it and fail at the unique-constraint level instead. This is
        // the safety net for that race.
        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(UserAlreadyExistException.class)
                .hasMessage("A user with this username or email is already registered");

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---- getCurrentUser -------------------------------------------------

    @Test
    void getCurrentUser_returnsTheMappedProfile_whenTheUserExists() {
        User user = new User();
        user.setId(5L);
        user.setNotarianId("BADAZOP");
        user.setUsername("johndoe");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john@example.com");
        user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        CurrentUserResponse response = service.getCurrentUser(5L);

        assertThat(response).isEqualTo(new CurrentUserResponse(
                5L, "BADAZOP", "johndoe", "John", "Doe", "john@example.com",
                LocalDateTime.of(2026, 1, 1, 0, 0)));
    }

    @Test
    void getCurrentUser_throwsUserNotFoundException_whenTheIdHasNoRow() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUser(404L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found.");
    }

    // ---- lookupUsers -------------------------------------------------

    @Test
    void lookupUsers_throwsInvalidUserLookupException_whenCollectionIsNull() {
        assertThatThrownBy(() -> service.lookupUsers(null))
                .isInstanceOf(InvalidUserLookupException.class)
                .hasMessage("At least one id is required.");
        verifyNoInteractions(userRepository);
    }

    @Test
    void lookupUsers_throwsInvalidUserLookupException_whenCollectionIsEmpty() {
        assertThatThrownBy(() -> service.lookupUsers(List.of()))
                .isInstanceOf(InvalidUserLookupException.class)
                .hasMessage("At least one id is required.");
    }

    @Test
    void lookupUsers_throwsInvalidUserLookupException_whenOnlyElementIsNull() {
        List<Long> ids = new ArrayList<>();
        ids.add(null);

        assertThatThrownBy(() -> service.lookupUsers(ids))
                .isInstanceOf(InvalidUserLookupException.class)
                .hasMessage("At least one id is required.");
        verifyNoInteractions(userRepository);
    }

    @Test
    void lookupUsers_dedupesAndDropsNulls_beforeQuerying_preservingInsertionOrder() {
        List<Long> ids = Arrays.asList(3L, 1L, null, 3L, 2L, 1L);
        when(userRepository.findByIdIn(any())).thenReturn(List.of());

        service.lookupUsers(ids);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userRepository).findByIdIn(captor.capture());
        assertThat(captor.getValue()).containsExactly(3L, 1L, 2L);
    }

    @Test
    void lookupUsers_throwsInvalidUserLookupException_whenDistinctCountExceedsTheCeiling() {
        List<Long> ids = new ArrayList<>();
        for (long i = 0; i < MAX_USER_LOOKUP_IDS + 1; i++) {
            ids.add(i);
        }

        assertThatThrownBy(() -> service.lookupUsers(ids))
                .isInstanceOf(InvalidUserLookupException.class)
                .hasMessageContaining("At most " + MAX_USER_LOOKUP_IDS);
        verifyNoInteractions(userRepository);
    }

    @Test
    void lookupUsers_allowsExactlyTheCeilingCount() {
        List<Long> ids = new ArrayList<>();
        for (long i = 0; i < MAX_USER_LOOKUP_IDS; i++) {
            ids.add(i);
        }
        when(userRepository.findByIdIn(any())).thenReturn(List.of());

        assertThatCode(() -> service.lookupUsers(ids)).doesNotThrowAnyException();
    }

    @Test
    void lookupUsers_mapsEachRepositoryRowToASummary() {
        User a = new User();
        a.setId(1L); a.setUsername("alice"); a.setFirstName("Alice"); a.setLastName("A");
        User b = new User();
        b.setId(2L); b.setUsername("bob"); b.setFirstName("Bob"); b.setLastName("B");
        when(userRepository.findByIdIn(any())).thenReturn(List.of(a, b));

        List<UserSummaryResponse> result = service.lookupUsers(List.of(1L, 2L));

        assertThat(result).containsExactly(
                new UserSummaryResponse(1L, "alice", "Alice", "A"),
                new UserSummaryResponse(2L, "bob", "Bob", "B"));
    }

    // ---- searchUsers -------------------------------------------------

    @Test
    void searchUsers_returnsAnEmptyPage_whenQueryIsNull_withoutTouchingTheRepository() {
        Pageable pageable = Pageable.unpaged();

        PagedResponse<UserSummaryResponse> result = service.searchUsers(10L, null, pageable);

        assertThat(result.content()).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void searchUsers_returnsAnEmptyPage_whenQueryIsBlank_withoutTouchingTheRepository() {
        // Privacy: an empty term must not fall through to listing every user,
        // unlike note/transcript search where blank means "list mine".
        Pageable pageable = Pageable.unpaged();

        PagedResponse<UserSummaryResponse> result = service.searchUsers(10L, "   ", pageable);

        assertThat(result.content()).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void searchUsers_stripsAndEscapesTheTerm_beforeDelegatingToTheRepository() {
        Pageable pageable = Pageable.unpaged();
        String rawTerm = " Jo%hn_ ";
        String expectedEscaped = LikeEscaper.escape(rawTerm.strip());
        Page<User> emptyPage = Page.empty(pageable);
        when(userRepository.searchUsers(eq(10L), eq(expectedEscaped), eq(pageable))).thenReturn(emptyPage);

        service.searchUsers(10L, rawTerm, pageable);

        verify(userRepository).searchUsers(10L, expectedEscaped, pageable);
    }

    @Test
    void searchUsers_mapsRepositoryContentToSummaries() {
        Pageable pageable = Pageable.unpaged();
        User user = new User();
        user.setId(3L); user.setUsername("carol"); user.setFirstName("Carol"); user.setLastName("C");
        Page<User> page = new org.springframework.data.domain.PageImpl<>(List.of(user));
        when(userRepository.searchUsers(any(), anyString(), any())).thenReturn(page);

        PagedResponse<UserSummaryResponse> result = service.searchUsers(10L, "carol", pageable);

        assertThat(result.content()).containsExactly(new UserSummaryResponse(3L, "carol", "Carol", "C"));
    }

    // ---- existsById -------------------------------------------------

    @Test
    void existsById_delegatesDirectlyToTheRepository() {
        when(userRepository.existsById(42L)).thenReturn(true);

        assertThat(service.existsById(42L)).isTrue();
        verify(userRepository).existsById(42L);
    }
}