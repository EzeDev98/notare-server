package com.notare.feature.user.event;

/** Event telling each module to clear what is left that mentions a deleted user. */
public record UserDeletionSweep(Long userId) {
}
