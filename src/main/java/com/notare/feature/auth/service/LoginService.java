package com.notare.feature.auth.service;

import com.notare.feature.auth.dto.request.LoginRequest;
import com.notare.feature.auth.dto.response.UserLoginResponse;

/** Signs users in. */
public interface LoginService {
    UserLoginResponse login(LoginRequest request, String userAgent, String ipAddress);
}
