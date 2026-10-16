package com.notare.feature.share.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /voice-notes/{id}/copy (step 17f, B-05). The source is in the path and the owner of
 * the copy is the caller, so the body holds only where the copy goes and the id that makes a
 * retry store one copy, not two.
 */
@Getter
@Setter
@NoArgsConstructor
public class CopyVoiceNoteRequest {

    /** One of the caller's own notes. */
    @NotNull(message = "targetNoteId is required.")
    private Long targetNoteId;

    /** The copy's id on the device. Unique per owner, like the clientId of an upload. */
    @NotBlank(message = "clientId is required.")
    @Size(max = 64, message = "clientId must not exceed 64 characters.")
    private String clientId;
}
