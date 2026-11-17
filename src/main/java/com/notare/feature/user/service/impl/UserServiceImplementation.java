package com.notare.feature.user.service.impl;

import com.notare.client.dto.request.RegisterRequest;
import com.notare.client.dto.response.InternalAuthApiResponse;
import com.notare.client.dto.response.RegisterResponse;
import com.notare.client.service.InternalAuthClient;
import com.notare.common.exception.*;
import com.notare.feature.auth.validation.RegistrationValidator;
import com.notare.feature.user.dto.request.RegistrationRequest;
import com.notare.feature.user.dto.response.CurrentUserResponse;
import com.notare.feature.user.dto.response.RegistrationResponse;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.event.UserRegisteredEvent;
import com.notare.feature.user.model.User;
import com.notare.feature.user.repository.UserRepository;
import com.notare.feature.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.notare.common.constant.GeneralConstant.MAX_ID_GENERATION_ATTEMPTS;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserServiceImplementation implements UserService {

    private final NotarianIdGenerator notarianIdGenerator;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RegistrationValidator registrationValidator;
    private final InternalAuthClient authClient;


    @Override
    @Transactional
    public RegistrationResponse registerUser(RegistrationRequest request) {

        registrationValidator.validateRegistrationRequest(request);

        String username = request.getUsername() == null ? null : request.getUsername().trim();

        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase();

        Optional<User> existingUser = userRepository.findByUsernameOrEmail(username, email);

        if (existingUser.isPresent()) {
            User found = existingUser.get();

            if (email != null && found.getEmail().equals(email)) {
                throw new UserAlreadyExistException("A user with this email is already registered");
            }
            if (username != null && found.getUsername().equals(username)) {
                throw new UserAlreadyExistException("This username is already taken");
            }
        }

        // Send the normalized values, not the raw request ones - otherwise IAM
        // and Notare can end up holding two different casings/whitespace
        // variants of the same email for one person.
        RegisterRequest registerRequest = new RegisterRequest(username, null, email, request.getPassword(), request.getConfirmPassword());

        InternalAuthApiResponse<RegisterResponse> registerResponse = authClient.register(registerRequest);

        RegisterResponse response = registerResponse == null ? null : registerResponse.getData();

        if (response == null || response.getInternalAuthUserId() == null) {
            throw new RegistrationException("internal-auth-server did not return an account id.");
        }

        try {
            User user = createUser(request, response.getInternalAuthUserId());
            eventPublisher.publishEvent(new UserRegisteredEvent(user.getId()));
            return new RegistrationResponse(HttpServletResponse.SC_CREATED, "Account created. Please check your email to verify your account.", user.getNotarianId());
        } catch (DataIntegrityViolationException ex) {
            rollbackIamUser(response.getInternalAuthUserId());
            throw new UserAlreadyExistException("A user with this username or email is already registered");
        } catch (NotarianIdGenerationException ex) {
            // Not a duplicate-account problem: keep the real failure, but
            // still remove the IAM account so the person can retry later.
            rollbackIamUser(response.getInternalAuthUserId());
            throw ex;
        }
    }

    /**
     * Best-effort cleanup of an IAM account created before the local save
     * failed. If the delete itself fails, log it and let the caller surface
     * the original failure rather than a downstream cleanup error.
     */
    private void rollbackIamUser(String internalAuthUserId) {
        try {
            authClient.deleteUser(internalAuthUserId);
        } catch (Exception rollbackEx) {
            log.warn("Failed to roll back orphaned internal-auth-server user {} after local registration failure", internalAuthUserId, rollbackEx);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long userId) {
        // The id comes from the authenticated principal, so a miss here means
        // the account was deleted while its token was still valid rather than
        // that someone asked for a stranger. 404 either way; there is nothing
        // to protect, since the caller already proved they are this user.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found."));

        return new CurrentUserResponse(
                user.getId(),
                user.getNotarianId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getCreatedAt());
    }

    private User createUser(RegistrationRequest request, String interAuthUserId) {

        User user = new User();
        user.setInternalAuthId(interAuthUserId);
        user.setNotarianId(generateUniqueNotarianId());
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setUsername(request.getUsername().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setUserStatus(UserStatus.PENDING_VERIFICATION);
        user.setEmailVerified(false);

        return userRepository.save(user);
    }


    private String generateUniqueNotarianId() {

        for (int attempt = 0; attempt < MAX_ID_GENERATION_ATTEMPTS; attempt++) {
            String candidate = notarianIdGenerator.generateId();

            if (!userRepository.existsByNotarianId(candidate)) {
                return candidate;
            }
        }

        throw new NotarianIdGenerationException("Unable to generate a unique notarianId after " + MAX_ID_GENERATION_ATTEMPTS + " attempts");
    }
}