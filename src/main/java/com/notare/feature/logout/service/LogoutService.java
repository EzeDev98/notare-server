package com.notare.feature.logout.service;

import com.notare.feature.logout.dto.response.LogoutResponse;

public interface LogoutService {
    LogoutResponse logout(String accessToken);

    LogoutResponse logoutAll(String accessToken);
}
