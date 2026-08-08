package com.notare.feature.user.dto.response;

import com.notare.feature.user.enums.Gender;

import java.time.LocalDateTime;

/**
 * The authenticated caller's own profile — the answer to "who am I".
 *
 * <h2>Why this is not UserSummaryResponse</h2>
 *
 * <p>{@link UserSummaryResponse} is the PUBLIC view of a user: id, username,
 * first and last name, and deliberately no email, because it is handed to
 * strangers through search and lookup. This is the caller's own record, so the
 * privacy calculation is different — your own email address is not a leak, and
 * an account screen that cannot show it is missing the obvious thing.
 *
 * <p>Two records rather than one with conditional fields. A DTO whose contents
 * depend on who is asking is a DTO whose contents someone will eventually get
 * wrong, and the failure is silent in exactly the direction that matters.
 *
 * <h2>Why the id is the point</h2>
 *
 * <p>Everything else here is convenience. The id is the reason this endpoint
 * had to exist: {@code LoginResponse} returns notarianId and username, while
 * every other payload in the API identifies people by numeric id — ownerId,
 * actorId, lastModifiedBy, sharedWithUserId, collaboratorId. Without this, a
 * client holds a valid session and still cannot answer "is this me?" about any
 * of them. It cannot tell an invitation it sent from one it received, cannot
 * decide whether "edited by" means someone else, and cannot phrase a
 * notification as "you" rather than a stranger's name.
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
        Gender gender,
        LocalDateTime createdAt
) {
}