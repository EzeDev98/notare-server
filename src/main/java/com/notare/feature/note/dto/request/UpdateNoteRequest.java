package com.notare.feature.note.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body for replacing a note. */
@Getter
@Setter
@NoArgsConstructor
public class UpdateNoteRequest {

    private String content;

    @NotNull(message = "version is required.")
    private Long version;
}
