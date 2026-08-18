package com.notare.feature.voiceshare.dto.request;

import com.notare.feature.share.enums.SharePermission;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /voice-notes/{voiceNoteId}/shares — the owner grants someone
 * access.
 *
 * <p>Mirrors {@link com.notare.feature.share.dto.request.ShareNoteRequest}
 * exactly. The voice note comes from the path and the owner from the
 * authenticated principal, so the body carries only the two things the
 * caller genuinely chooses: who, and at what level. An ownerId in the body
 * would let anyone share voice notes on another user's behalf.
 */
@Getter
@Setter
@NoArgsConstructor
public class ShareVoiceNoteRequest {

    /** The recipient. Whether they exist, and whether they're an accepted collaborator, is the service's call — both need a database lookup. */
    @NotNull(message = "sharedWithUserId is required.")
    private Long sharedWithUserId;

    /**
     * Required rather than defaulting to READ, same reasoning as
     * ShareNoteRequest: a silent default on a permissions field means a
     * client that forgets to send it gets a grant it never asked for, and
     * the mistake is invisible because the request succeeds.
     *
     * <p>Jackson binds the enum by name ("READ"/"WRITE"), and an unrecognised
     * value fails deserialization, which Spring MVC already answers with a 400.
     */
    @NotNull(message = "permission is required (READ or WRITE).")
    private SharePermission permission;
}
