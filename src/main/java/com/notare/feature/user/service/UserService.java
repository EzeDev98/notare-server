package com.notare.feature.user.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.user.dto.request.RegistrationRequest;
import com.notare.feature.user.dto.response.CurrentUserResponse;
import com.notare.feature.user.dto.response.RegistrationResponse;
import com.notare.feature.user.dto.response.UserSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface UserService {

    RegistrationResponse registerUser(RegistrationRequest request);

    /**
     * The caller's own profile, including the numeric id that every other
     * payload in the API uses to identify people.
     */
    CurrentUserResponse getCurrentUser(Long userId);

    /**
     * Resolve a batch of user ids to public profiles.
     *
     * <p>Part of the user module's PUBLIC API. Other modules return bare ids by
     * design — enriching each row would mean a user lookup per row — and this
     * is the endpoint that lets a client turn a page of those ids into names in
     * one request.
     *
     * <p>Unknown ids are omitted rather than reported. A batch that fails
     * because one referenced user has since been deleted would be useless to
     * the caller that needs the other nineteen.
     */
    List<UserSummaryResponse> lookupUsers(Collection<Long> ids);

    /** Search for other users to invite as collaborators. */
    PagedResponse<UserSummaryResponse> searchUsers(Long currentUserId, String query, Pageable pageable);

    /**
     * Whether a user with this id exists. Part of the user module's PUBLIC API so
     * other modules (e.g. collaboration, when validating an invite) can check a
     * user's existence without reaching into the user repository directly.
     */
    boolean existsById(Long userId);
}