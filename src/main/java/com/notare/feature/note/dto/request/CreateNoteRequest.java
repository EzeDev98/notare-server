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
     * @NotBlank here. Note text has no length limit.
     */
    private String content;

    /**
     * Optional: the id the client gave this note locally. Posting the same one again returns the
     * note it already made (200) instead of a second one (step 17a).
     */
    @Size(min = 1, max = 64, message = "clientId must be 1 to 64 characters.")
    private String clientId;
}