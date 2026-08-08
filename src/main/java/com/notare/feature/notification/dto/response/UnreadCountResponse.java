package com.notare.feature.notification.dto.response;

/**
 * The unread badge count.
 *
 * <p>An object wrapping one number rather than returning a bare {@code 7}. A
 * top-level JSON scalar is valid but a dead end: the day you want to add
 * "oldest unread timestamp" or a per-type breakdown, changing {@code 7} to
 * {@code {...}} breaks every client at once, whereas adding a field to an
 * object breaks none of them.
 */
public record UnreadCountResponse(long unreadCount) {
}