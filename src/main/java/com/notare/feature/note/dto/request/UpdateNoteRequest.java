package com.notare.feature.note.dto.request;

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
}