package com.notare.feature.user.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.user.dto.request.RegistrationRequest;
import com.notare.feature.user.dto.response.RegistrationResponse;
import com.notare.feature.user.dto.response.UserSummaryResponse;
import org.springframework.data.domain.Pageable;

public interface UserService {

    RegistrationResponse registerUser(RegistrationRequest request);

    /** Search for other users to invite as collaborators. */
    PagedResponse<UserSummaryResponse> searchUsers(Long currentUserId, String query, Pageable pageable);

    /**
     * Whether a user with this id exists. Part of the user module's PUBLIC API so
     * other modules (e.g. collaboration, when validating an invite) can check a
     * user's existence without reaching into the user repository directly.
     */
    boolean existsById(Long userId);
}
