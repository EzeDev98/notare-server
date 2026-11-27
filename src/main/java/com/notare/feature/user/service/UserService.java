package com.notare.feature.user.service;

import com.notare.feature.user.dto.request.RegistrationRequest;
import com.notare.feature.user.dto.response.AiTidySettingResponse;
import com.notare.feature.user.dto.response.CurrentUserResponse;
import com.notare.feature.user.dto.response.RegistrationResponse;


public interface UserService {

    RegistrationResponse registerUser(RegistrationRequest request);

    /**
     * The caller's own profile, including the numeric id that every other
     * payload in the API uses to identify people.
     */
    CurrentUserResponse getCurrentUser(Long userId);

    /** Whether AI tidy is on for this user. Off until they turn it on. */
    AiTidySettingResponse getAiTidy(Long userId);

    AiTidySettingResponse setAiTidy(Long userId, boolean enabled);
}
