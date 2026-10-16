package com.notare.feature.user.event;

/**
 * Published after {@link UserDeletionRequested} has been committed, once the deletes it caused (note
 * tombstones, notifications about shares ending) have all run. A module clears what is still left
 * that mentions this user: references on other people's rows, residue of its own cascades.
 */
public record UserDeletionSweep(Long userId) {
}
