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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.notare.common.constant.GeneralConstant.MAX_ID_GENERATION_ATTEMPTS;
import static com.notare.common.constant.GeneralConstant.MAX_USER_LOOKUP_IDS;

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

        String username = request.getUsername() == null
                ? null
                : request.getUsername().trim();

        String email = request.getEmail() == null
                ? null
                : request.getEmail().trim().toLowerCase();

        Optional<User> existingUser = userRepository.findByUsernameOrEmail(username, email);
        existingUser.ifPresent(userFound -> {
            if (userFound.getUsername().equals(username)) {
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

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> lookupUsers(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidUserLookupException("At least one id is required.");
        }

        // Deduplicate BEFORE the size check, and keep insertion order so the
        // response is stable for a client that requested a stable list.
        // Checking the raw count first would reject a legitimate request that
        // happened to repeat an id — a page of twenty notifications from the
        // same actor is twenty identical ids and one distinct user.
        Set<Long> distinctIds = new LinkedHashSet<>(ids);

        // Nulls in a query parameter list are possible (a client sending
        // "?ids=1,,3" binds an empty element) and would produce a broken IN
        // clause. Dropped rather than rejected: a stray comma is not worth a
        // 400 when the intent is unambiguous.
        distinctIds.remove(null);

        if (distinctIds.isEmpty()) {
            throw new InvalidUserLookupException("At least one id is required.");
        }
        if (distinctIds.size() > MAX_USER_LOOKUP_IDS) {
            throw new InvalidUserLookupException(
                    "At most " + MAX_USER_LOOKUP_IDS + " ids may be looked up in one request.");
        }

        // Ids with no row are simply absent from the response. That includes
        // the reserved system auditor id 0, which appears in lastModifiedBy for
        // rows written by the transcription worker or the retention job and is
        // never a real user — a client must render those as the system rather
        // than as "user 0", and getting nothing back is the correct signal.
        return userRepository.findByIdIn(distinctIds).stream()
                .map(this::toSummary)
                .toList();
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

        // Escaped only after the blank check above. The two guards work
        // together: the blank check stops an empty term from listing the
        // directory, and escaping stops a term of "%" — which is not blank —
        // from doing the same thing by another route.
        Page<User> page = userRepository.searchUsers(currentUserId, LikeEscaper.escape(term), pageable);
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