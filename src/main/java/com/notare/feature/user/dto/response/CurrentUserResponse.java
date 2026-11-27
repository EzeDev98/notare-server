package com.notare.feature.user.dto.response;

import java.time.LocalDateTime;

/** The signed-in user's own profile. */
public record CurrentUserResponse(
        Long id,
        String notarianId,
        String username,
        String firstName,
        String lastName,
        String email,
        LocalDateTime createdAt
) {
}
