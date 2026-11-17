package com.notare.feature.user.dto.response;

import java.time.LocalDateTime;

/**
 * The authenticated caller's own profile — the answer to "who am I".
 *
 * <h2>Why the id is the point</h2>
 *
 * <p>Everything else here is convenience. The id is the reason this endpoint
 * had to exist: {@code LoginResponse} returns notarianId and username, while
 * every other payload in the API identifies people by numeric id — ownerId,
 * lastModifiedBy. Without this, a client holds a valid session and still cannot
 * answer "is this me?" about any of them.
 *
 * <h2>What is deliberately absent</h2>
 *
 * <p>No {@code emailVerified} and no {@code userStatus}. Login throws
 * DisabledException for an unverified account and LockedException for a locked
 * one, so any caller holding a token to reach this endpoint is verified and
 * active. Both fields would be constants, and a field that can never disagree
 * with the fact you already have is duplication that looks like rigour. Worth
 * adding the day a status exists that can still authenticate.
 *
 * <p>No password hash, obviously, and no session information — sessions have
 * their own endpoint.
 */
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