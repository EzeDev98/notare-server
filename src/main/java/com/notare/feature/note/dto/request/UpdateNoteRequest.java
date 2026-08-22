package com.notare.feature.note.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateNoteRequest {

    @Size(max = 20_000, message = "Content must not exceed 20,000 characters.")
    private String content;

    /**
     * The version the client last loaded. Required now: the server compares it
     * against the note's current version to detect whether someone else edited
     * the note in the meantime. The client gets this value from any note
     * response and simply echoes it back on save.
     */
    @NotNull(message = "Version is required so we can detect edit conflicts.")
    private Long version;
}