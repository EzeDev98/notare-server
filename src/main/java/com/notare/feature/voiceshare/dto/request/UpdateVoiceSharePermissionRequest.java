package com.notare.feature.voiceshare.dto.request;

import com.notare.feature.share.enums.SharePermission;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of PUT /voice-notes/{voiceNoteId}/shares/{userId} — promote READ to
 * WRITE, or demote back.
 *
 * <p>Mirrors
 * {@link com.notare.feature.share.dto.request.UpdateSharePermissionRequest}:
 * a separate DTO rather than reusing {@link ShareVoiceNoteRequest}, since
 * both the voice note and the recipient are already in the path, so the only
 * thing left to send is the new level.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateVoiceSharePermissionRequest {

    @NotNull(message = "permission is required (READ or WRITE).")
    private SharePermission permission;
}
