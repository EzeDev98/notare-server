package com.notare.feature.note.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body for creating a note. */
@Getter
@Setter
@NoArgsConstructor
public class CreateNoteRequest {

    private String content;

    @Size(min = 1, max = 64, message = "clientId must be 1 to 64 characters.")
    private String clientId;
}
