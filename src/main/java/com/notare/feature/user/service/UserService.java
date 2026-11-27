package com.notare.feature.user.service;

import com.notare.feature.user.dto.request.RegistrationRequest;
import com.notare.feature.user.dto.response.AiTidySettingResponse;
import com.notare.feature.user.dto.response.CurrentUserResponse;
import com.notare.feature.user.dto.response.RegistrationResponse;

/** Registration, the current user's profile and the AI tidy setting. */
public interface UserService {

    RegistrationResponse registerUser(RegistrationRequest request);

    CurrentUserResponse getCurrentUser(Long userId);

    AiTidySettingResponse getAiTidy(Long userId);

    AiTidySettingResponse setAiTidy(Long userId, boolean enabled);
}
