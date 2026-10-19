package com.notare.feature.note.dto.request;

import jakarta.validation.constraints.NotNull;
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

    private String content;

    /**
     * The version of the note this edit was made from: the value last returned by the server, echoed
     * back unchanged (a token, not a counter). A stale one is refused with 409 and the current version.
     * Required on the REST endpoints; the sync path has its own operation ledger and sends none.
     */
    @NotNull(message = "version is required.")
    private Long version;
}