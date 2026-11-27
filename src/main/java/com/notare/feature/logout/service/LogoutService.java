package com.notare.feature.logout.service;

import com.notare.feature.logout.dto.response.LogoutResponse;

/** Ends a user's session. */
public interface LogoutService {
    LogoutResponse logout(String accessToken);
}
