package com.notare.feature.share.dto.request;

import com.notare.feature.share.enums.SharePermission;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /notes/{noteId}/shares — the owner grants someone access.
 *
 * <p>The note comes from the path and the owner from the authenticated
 * principal, so the body carries only the two things the caller genuinely
 * chooses: who, and at what level. An ownerId in the body would let anyone
 * share notes on another user's behalf.
 */
@Getter
@Setter
@NoArgsConstructor
public class ShareNoteRequest {

    /** The recipient. Whether they exist, and whether they're an accepted collaborator, is the service's call — both need a database lookup. */
    @NotNull(message = "sharedWithUserId is required.")
    private Long sharedWithUserId;

    /**
     * Required rather than defaulting to READ. A silent default on a
     * permissions field means a client that forgets to send it, or sends a
     * typo'd key name, gets a grant it never asked for — and the mistake is
     * invisible because the request succeeds. Making it explicit turns that
     * into a 400.
     *
     * <p>Jackson binds the enum by name ("READ"/"WRITE"), and an unrecognised
     * value fails deserialization, which Spring MVC already answers with a 400 —
     * so no extra handler is needed to reject a made-up permission level.
     */
    @NotNull(message = "permission is required (READ or WRITE).")
    private SharePermission permission;
}