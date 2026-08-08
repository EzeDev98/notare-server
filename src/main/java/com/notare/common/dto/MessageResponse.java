package com.notare.common.dto;

/**
 * A plain human-readable message, as JSON.
 *
 * <p>Replaces the bare {@code String} bodies that OTP verification, OTP resend
 * and the three password-reset endpoints used to return. Those responses were
 * {@code text/plain}, which made them the only successful responses in the API
 * a client could not run through {@code response.json()} — so every HTTP layer
 * built against this server needed a per-endpoint exception before it could
 * parse a 200.
 *
 * <p>An object rather than a top-level JSON string for the same reason as
 * {@code UnreadCountResponse}: a scalar body is a dead end. The day one of
 * these wants to return a cooldown expiry alongside its message, an object
 * grows a field and a string breaks every client at once.
 */
public record MessageResponse(String message) {
}