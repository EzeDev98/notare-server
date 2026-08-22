package com.notare.feature.note.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateNoteRequest {

    /**
     * Optional: the spec allows creating an empty note, so there is no
     * @NotBlank here. The 20,000-character ceiling is tied to the TEXT
     * column on the Note entity (see the note in step 1C's writeup).
     */
    @Size(max = 20_000, message = "Content must not exceed 20,000 characters.")
    private String content;
}