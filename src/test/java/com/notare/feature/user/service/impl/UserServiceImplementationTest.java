package com.notare.feature.user.service.impl;

import com.notare.common.exception.NotarianIdGenerationException;
import com.notare.common.exception.RegistrationException;
import com.notare.client.dto.response.InternalAuthApiResponse;
import com.notare.client.dto.response.RegisterResponse;
import com.notare.common.exception.UserAlreadyExistException;
import com.notare.common.exception.UserNotFoundException;
import com.notare.feature.user.dto.request.RegistrationRequest;
import com.notare.feature.user.dto.response.CurrentUserResponse;
import com.notare.feature.user.dto.response.RegistrationResponse;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.event.UserRegisteredEvent;
import com.notare.feature.user.model.User;
import com.notare.feature.user.repository.UserRepository;
import com.notare.feature.auth.validation.RegistrationValidator;
import com.notare.client.service.InternalAuthClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests for UserServiceImplementation. */
@ExtendWith(MockitoExtension.class)
public class UserServiceImplementationTest {

    private static final int MAX_ID_GENERATION_ATTEMPTS = 10;

    @Mock private NotarianIdGenerator notarianIdGenerator;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private RegistrationValidator registrationValidator;
    @Mock private InternalAuthClient authClient;

    public UserServiceImplementation service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImplementation(notarianIdGenerator, userRepository, eventPublisher, registrationValidator, authClient);
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

    private void stubIamRegister() {
        when(authClient.register(any()))
                .thenReturn(InternalAuthApiResponse.success(new RegisterResponse("iam-1", "johndoe", "ok")));
    }

    private User existingUser(String username, String email) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }

    @Test
    void registerUser_propagatesValidatorFailure_beforeAnyRepositoryOrIamInteraction() {
        RegistrationRequest request = registrationRequest();
        request.setConfirmPassword("SomethingElse1");
        doThrow(new RegistrationException("Passwords do not match"))
                .when(registrationValidator).validateRegistrationRequest(request);

        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(RegistrationException.class)
                .hasMessage("Passwords do not match");

        verifyNoInteractions(userRepository, notarianIdGenerator, eventPublisher, authClient);
    }

    @Test
    void registerUser_throwsUserAlreadyExistException_whenUsernameIsTaken() {
        RegistrationRequest request = registrationRequest();
        when(userRepository.findByUsernameOrEmail("johndoe", "john@example.com"))
                .thenReturn(Optional.of(existingUser("johndoe", "someoneelse@example.com")));

        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(UserAlreadyExistException.class)
                .hasMessage("This username is already taken");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_throwsUserAlreadyExistException_whenOnlyEmailIsTaken() {
        RegistrationRequest request = registrationRequest();
        when(userRepository.findByUsernameOrEmail("johndoe", "john@example.com"))
                .thenReturn(Optional.of(existingUser("someoneelse", "john@example.com")));

        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(UserAlreadyExistException.class)
                .hasMessage("A user with this email is already registered");
    }

    @Test
    void registerUser_trimsAndNormalizesFields_andSetsInitialStatus() {
        RegistrationRequest request = registrationRequest();
        stubIamRegister();
        when(userRepository.findByUsernameOrEmail("johndoe", "john@example.com")).thenReturn(Optional.empty());
        when(notarianIdGenerator.generateId()).thenReturn("BADAZOP");
        when(userRepository.existsByNotarianId("BADAZOP")).thenReturn(false);
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
        stubIamRegister();
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
        stubIamRegister();
        when(userRepository.findByUsernameOrEmail("johndoe", "john@example.com")).thenReturn(Optional.empty());
        when(notarianIdGenerator.generateId()).thenReturn("TAKEN11", "FREEID1");
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
        stubIamRegister();
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
        stubIamRegister();
        when(userRepository.findByUsernameOrEmail("johndoe", "john@example.com")).thenReturn(Optional.empty());
        when(notarianIdGenerator.generateId()).thenReturn("BADAZOP");
        when(userRepository.existsByNotarianId("BADAZOP")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.registerUser(request))
                .isInstanceOf(UserAlreadyExistException.class)
                .hasMessage("A user with this username or email is already registered");

        verify(eventPublisher, never()).publishEvent(any());
    }

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

}
