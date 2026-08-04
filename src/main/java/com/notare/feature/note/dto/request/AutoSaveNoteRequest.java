package com.notare.feature.note.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for the auto-save (partial update) endpoint.
 *
 * Structurally identical to UpdateNoteRequest today, but kept separate on
 * purpose: the two endpoints have DIFFERENT null-handling (auto-save treats a
 * null field as "leave it alone"; the full update treats null as "clear it"),
 * and keeping the contracts separate lets each evolve on its own — auto-save
 * may later grow a client-side timestamp or debounce token that a full save
 * has no use for.
 */
@Getter
@Setter
@NoArgsConstructor
public class AutoSaveNoteRequest {

    @Size(max = 255, message = "Title must not exceed 255 characters.")
    private String title;

    @Size(max = 20_000, message = "Content must not exceed 20,000 characters.")
    private String content;

    @NotNull(message = "Version is required so we can detect edit conflicts.")
    private Long version;
}