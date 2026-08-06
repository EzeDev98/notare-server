package com.notare.feature.collaboration.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /collaborations — the owner names the user they want to invite.
 *
 * <p>Only the invitee's id is accepted. The owner is NEVER taken from the body:
 * it comes from the authenticated principal in the controller. Letting a client
 * supply an ownerId would let anyone send invitations on another user's behalf.
 *
 * <p>The id is a plain Long rather than a username/email so the client goes
 * through user search (6B) first — that endpoint already decides what is
 * publicly resolvable, and this one stays a dumb id reference.
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateInvitationRequest {

    /**
     * The user being invited. @NotNull (not @NotBlank — it's a number), so a
     * missing id fails validation with a 400 before the service is ever called.
     * Whether the user actually exists is the service's job (-> 404), because
     * that needs a database lookup, not a format check.
     */
    @NotNull(message = "collaboratorId is required.")
    private Long collaboratorId;
}