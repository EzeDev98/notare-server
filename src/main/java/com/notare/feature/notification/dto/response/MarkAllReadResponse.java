package com.notare.feature.notification.dto.response;

/**
 * Result of marking everything read: how many rows actually changed.
 *
 * <p>Wrapped in an object rather than returned as a bare int, for the same
 * reason as UnreadCountResponse — a top-level JSON scalar can never grow a
 * second field without breaking every client at once.
 *
 * <p>Returned instead of a bare 204 because the number is genuinely useful:
 * the client can zero its badge AND know the request did something, without a
 * follow-up count request. Zero is a perfectly good answer, and means everything
 * was already read.
 */
public record MarkAllReadResponse(int markedCount) {
}