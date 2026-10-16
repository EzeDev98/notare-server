package com.notare.client.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Body for internal-auth-server's POST /auth/change-password (step 17g, B-02). */
@Getter
@AllArgsConstructor
public class InternalChangePasswordRequest {
    private final String username;
    private final String currentPassword;
    private final String newPassword;
}
