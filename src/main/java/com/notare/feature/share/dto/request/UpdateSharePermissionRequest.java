package com.notare.feature.share.dto.request;

import com.notare.feature.share.enums.SharePermission;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of PUT /notes/{noteId}/shares/{userId} — promote READ to WRITE, or
 * demote back.
 *
 * <p>A separate DTO rather than reusing {@link ShareNoteRequest}: on this
 * endpoint both the note and the recipient are already in the path, so the
 * only thing left to send is the new level. Reusing the create DTO would put a
 * sharedWithUserId in the body that either duplicates the path or contradicts
 * it — and then someone has to decide which one wins. A request shape that
 * can't express the contradiction is better than a rule about resolving it.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateSharePermissionRequest {

    @NotNull(message = "permission is required (READ or WRITE).")
    private SharePermission permission;
}