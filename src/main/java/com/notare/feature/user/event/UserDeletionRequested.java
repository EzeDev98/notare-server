package com.notare.feature.user.event;

/** Event telling each module to delete the data it owns for a user being deleted. */
public record UserDeletionRequested(Long userId) {
}
