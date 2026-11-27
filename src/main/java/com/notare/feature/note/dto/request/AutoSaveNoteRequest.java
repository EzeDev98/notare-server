package com.notare.feature.note.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body for saving part of a note. */
@Getter
@Setter
@NoArgsConstructor
public class AutoSaveNoteRequest {

    private String content;

    @NotNull(message = "version is required.")
    private Long version;
}
