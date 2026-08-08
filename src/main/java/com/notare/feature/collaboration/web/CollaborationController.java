package com.notare.feature.collaboration.web;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.collaboration.dto.request.CreateInvitationRequest;
import com.notare.feature.collaboration.dto.response.CollaborationResponse;
import com.notare.feature.collaboration.service.CollaborationService;
import com.notare.feature.user.model.UserDetail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * HTTP surface for the collaboration relationship (step 6).
 *
 * <p>Collaborations are a top-level resource, not a sub-resource of a user:
 * a collaboration is a relationship BETWEEN two users and belongs to neither,
 * so /users/{id}/collaborations would force an arbitrary choice of which side
 * owns it. /collaborations with "sent" and "received" views expresses both
 * sides of the same row cleanly.
 *
 * <p>Every endpoint derives the acting user from the authenticated principal
 * and hands it to the service, which enforces the actual rules: only the
 * invitee may accept or reject, only the owner may cancel. The controller does
 * no authorization of its own — it just supplies "who is asking".
 *
 * <p>The route needs no security change: WebSecurityConfig's allowlist covers
 * only specific POST auth paths and ends with anyRequest().authenticated(),
 * so /collaborations is protected by default.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/collaborations")
public class CollaborationController {

    private final CollaborationService collaborationService;

    /**
     * Send an invitation. 201 Created with a Location header, matching the
     * create-note convention — a new relationship row is genuinely created.
     *
     * <p>Failure modes come from the service, mapped by GlobalExceptionHandler:
     * inviting yourself -> 400, unknown invitee -> 404, already invited -> 409.
     */
    @PostMapping
    public ResponseEntity<CollaborationResponse> invite(
            @AuthenticationPrincipal UserDetail userDetail,
            @Valid @RequestBody CreateInvitationRequest request) {

        CollaborationResponse response = collaborationService.invite(
                userDetail.getUser().getId(), request.getCollaboratorId());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Accept an invitation addressed to me.
     *
     * <p>POST on an action sub-path rather than PATCH on the collaboration
     * itself: accepting is a named state transition with its own rules, not an
     * arbitrary field edit. Exposing PATCH /collaborations/{id} with a status
     * field would invite clients to write any status they liked and push the
     * state machine into the request body.
     *
     * <p>200 OK, not 201 — nothing new is created. Repeat calls are idempotent
     * (the service no-ops when already ACCEPTED); accepting an invitation that
     * was already rejected is a 400, and one that isn't addressed to me is a
     * 404 (a 403 would confirm the row exists).
     */
    @PostMapping("/{collaborationId}/accept")
    public ResponseEntity<CollaborationResponse> accept(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long collaborationId) {

        CollaborationResponse response = collaborationService.accept(
                userDetail.getUser().getId(), collaborationId);
        return ResponseEntity.ok(response);
    }

    /** Reject an invitation addressed to me. Mirror image of accept. */
    @PostMapping("/{collaborationId}/reject")
    public ResponseEntity<CollaborationResponse> reject(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long collaborationId) {

        CollaborationResponse response = collaborationService.reject(
                userDetail.getUser().getId(), collaborationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Owner withdraws an invitation, or removes an existing collaborator.
     * DELETE because the row really is removed, and 204 No Content because
     * there is nothing meaningful to return afterwards.
     *
     * <p>One resource, two verbs by role: the invitee's "no" is /reject (a
     * transition that leaves an auditable REJECTED row), while the owner's
     * withdrawal is a delete. They are different intents, so they are not
     * collapsed into one endpoint.
     */
    @DeleteMapping("/{collaborationId}")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long collaborationId) {

        collaborationService.cancel(userDetail.getUser().getId(), collaborationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Invitations I sent (I am the owner). Paged and newest-first: the most
     * recently touched invitation is the one a user is most likely looking for.
     */
    @GetMapping("/sent")
    public ResponseEntity<PagedResponse<CollaborationResponse>> listSent(
            @AuthenticationPrincipal UserDetail userDetail,
            @ParameterObject
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        PagedResponse<CollaborationResponse> response =
                collaborationService.listSent(userDetail.getUser().getId(), pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Invitations I received (I am the collaborator).
     *
     * <p>Two literal sub-paths instead of GET /collaborations?direction=sent:
     * they are two different queries against two different indexed columns, and
     * separate paths keep both the routing and the repository methods explicit.
     */
    @GetMapping("/received")
    public ResponseEntity<PagedResponse<CollaborationResponse>> listReceived(
            @AuthenticationPrincipal UserDetail userDetail,
            @ParameterObject
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        PagedResponse<CollaborationResponse> response =
                collaborationService.listReceived(userDetail.getUser().getId(), pageable);
        return ResponseEntity.ok(response);
    }
}