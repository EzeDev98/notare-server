package com.notare.feature.user.dto.response;

/**
 * Minimal PUBLIC profile returned by user search. Deliberately excludes email
 * (and everything else sensitive): even when you find someone BY typing their
 * email, the result never echoes an email back — that would leak addresses. The
 * id is what the invite flow needs; username + name are for display.
 */
public record UserSummaryResponse(
        Long id,
        String username,
        String firstName,
        String lastName
) {
}