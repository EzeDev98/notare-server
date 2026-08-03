package com.notare.feature.auth.service;

import com.notare.feature.auth.dto.request.LoginRequest;
import com.notare.feature.auth.dto.response.LoginResponse;

public interface LoginService {
    LoginResponse login(LoginRequest request);
}
