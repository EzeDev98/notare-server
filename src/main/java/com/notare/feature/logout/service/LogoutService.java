package com.notare.feature.logout.service;

import com.notare.feature.logout.dto.response.LogoutResponse;

public interface LogoutService {
    LogoutResponse logout(String sessionId, Long userId);
    LogoutResponse logoutAll(Long userId);
}
