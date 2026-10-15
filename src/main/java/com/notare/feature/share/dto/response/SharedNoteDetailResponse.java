package com.notare.feature.share.dto.response;

import java.time.LocalDateTime;

/**
 * A shared note as its recipient sees it (step 15g, B-13).
 *
 * <p>Only what a recipient needs. The owner's own view of the note, NoteResponse,
 * also carries pinned, archived, version, who last wrote it and when it was
 * created. Pinned and archived are the owner's private way of organising their
 * notes, so a recipient must never see them, and the rest is of no use to someone
 * who cannot save by version anyway.
 *
 * <p>{@code title} is derived from the first non-blank line of the body, the same
 * rule the client uses. {@code role} is "editor" or "viewer", the words the live
 * editing session already uses. {@code ownerName} is a display name, never an email.
 */
public record SharedNoteDetailResponse(
        Long id,
        String title,
        String body,
        String role,
        String ownerName,
        LocalDateTime updatedAt
) {
}
