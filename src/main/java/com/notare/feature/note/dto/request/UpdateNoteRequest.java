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
     * The version of the note this edit was made from: the value last returned by the server, echoed
     * back unchanged (a token, not a counter). A stale one is refused with 409 and the current version.
     * Required on the REST endpoints; the sync path has its own operation ledger and sends none.
     */
    @NotNull(message = "version is required.")
    private Long version;
}