package com.notare.feature.user.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.NotarianIdGenerationException;
import com.notare.common.exception.PasswordMisMatchException;
import com.notare.common.exception.UserAlreadyExistException;
import com.notare.feature.user.dto.request.RegistrationRequest;
import com.notare.feature.user.dto.response.RegistrationResponse;
import com.notare.feature.user.dto.response.UserSummaryResponse;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.event.UserRegisteredEvent;
import com.notare.feature.user.model.User;
import com.notare.feature.user.repository.UserRepository;
import com.notare.feature.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.notare.common.constant.GeneralConstant.MAX_ID_GENERATION_ATTEMPTS;

@RequiredArgsConstructor
@Service
public class UserServiceImplementation implements UserService {

    private final NotarianIdGenerator notarianIdGenerator;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;


    @Override
    @Transactional
    public RegistrationResponse registerUser(RegistrationRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMisMatchException("Passwords do not match");
        }

        Optional<User> existingUser = userRepository.findByUsernameOrEmail(request.getUsername(), request.getEmail());
        existingUser.ifPresent(userFound -> {
            if (userFound.getUsername().equals(request.getUsername())) {
                throw new UserAlreadyExistException("A user with this username is already registered");
            }
            throw new UserAlreadyExistException("A user with this email is already registered");
        });

        try {
            User user = createUser(request);
            eventPublisher.publishEvent(new UserRegisteredEvent(user.getId()));
            return new RegistrationResponse(HttpServletResponse.SC_CREATED, "Account created. Please check your email to verify your account.", user.getNotarianId());
        } catch (DataIntegrityViolationException ex) {
            throw new UserAlreadyExistException("A user with this username or email is already registered");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserSummaryResponse> searchUsers(Long currentUserId, String query, Pageable pageable) {
        String term = query == null ? "" : query.strip();

        // Privacy: a blank search must NOT dump the whole user directory. Unlike
        // note/transcript search (your OWN data, where blank = list all), this
        // searches OTHER users, so an empty term returns nothing.
        if (term.isEmpty()) {
            return PagedResponse.of(List.of(), Page.empty(pageable));
        }

        Page<User> page = userRepository.searchUsers(currentUserId, term, pageable);
        List<UserSummaryResponse> content = page.getContent().stream()
                .map(this::toSummary)
                .toList();
        return PagedResponse.of(content, page);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long userId) {
        return userRepository.existsById(userId);
    }

    private UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName());
    }

    private User createUser(RegistrationRequest request) {

        User user = new User();
        user.setNotarianId(generateUniqueNotarianId());
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setUsername(request.getUsername().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setGender(request.getGender());
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
