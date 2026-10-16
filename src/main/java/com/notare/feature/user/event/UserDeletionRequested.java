package com.notare.feature.user.event;

/**
 * An account is being deleted (step 17g, B-03). Each module deletes the data it owns for this user,
 * inside the deletion's transaction, so listen with a plain {@code @EventListener}: a BEFORE_COMMIT
 * listener could not publish events of its own. The module's deletes must be repeatable.
 */
public record UserDeletionRequested(Long userId) {
}
