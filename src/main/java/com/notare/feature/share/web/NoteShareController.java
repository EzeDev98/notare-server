package com.notare.feature.share.web;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.share.dto.request.ShareNoteRequest;
import com.notare.feature.share.dto.request.UpdateSharePermissionRequest;
import com.notare.feature.share.dto.response.NoteShareResponse;
import com.notare.feature.share.service.NoteShareService;
import com.notare.feature.user.model.UserDetail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * The owner's control panel for one note's access list.
 *
 * <p>Nested under the note, because a grant has no meaning apart from the note
 * it's on — unlike a collaboration, which is a standalone relationship and got
 * its own top-level collection in Step 6. The pattern matches the transcription
 * controller, which lives in its own module while its URL nests under
 * /voice-notes.
 *
 * <p>No clash with NoteController: it owns /notes, /notes/{id}, /notes/search
 * and a couple of action sub-paths, and this owns /notes/{id}/shares and below.
 * Distinct segments, so Spring routes them unambiguously.
 *
 * <p>Grants are addressed by RECIPIENT id, not by share id — DELETE
 * /notes/7/shares/42 reads as "stop sharing note 7 with user 42", which is the
 * operation the owner actually has in mind. The client already knows the user
 * id from user search; making it track an opaque share id as well would be
 * bookkeeping with no payoff.
 *
 * <p>Protected by default: the security allowlist covers only specific POST
 * auth routes and ends with anyRequest().authenticated().
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/notes/{noteId}/shares")
public class NoteShareController {

    private final NoteShareService noteShareService;

    /**
     * Grant access. 201 with a Location pointing at the grant's own address,
     * which is also where the client goes to change or revoke it.
     *
     * <p>Service-side failures: sharing with yourself or with someone who isn't
     * an accepted collaborator -> 400; not your note -> 404; already shared ->
     * 409 (change the level with PUT rather than re-POSTing).
     */
    @PostMapping
    public ResponseEntity<NoteShareResponse> shareNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId,
            @Valid @RequestBody ShareNoteRequest request) {

        NoteShareResponse response = noteShareService.shareNote(
                userDetail.getUser().getId(), noteId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sharedWithUserId}")
                .buildAndExpand(response.sharedWithUserId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /** Who currently has access to this note. Owner-scoped; someone else's note is a 404, not an empty page. */
    @GetMapping
    public ResponseEntity<PagedResponse<NoteShareResponse>> listShares(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        PagedResponse<NoteShareResponse> response =
                noteShareService.listShares(userDetail.getUser().getId(), noteId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Promote or demote an existing grant.
     *
     * <p>PUT rather than PATCH: the grant has exactly one mutable field, so
     * sending it IS sending the complete editable state. PATCH earns its place
     * when a resource has fields you might want to leave untouched — that's
     * why the note has both (PUT replaces, PATCH auto-saves), and why this
     * doesn't need the distinction.
     *
     * <p>Idempotent — setting the level it already has returns 200 unchanged
     * rather than erroring, which is what PUT is supposed to do.
     */
    @PutMapping("/{sharedWithUserId}")
    public ResponseEntity<NoteShareResponse> updatePermission(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId,
            @PathVariable Long sharedWithUserId,
            @Valid @RequestBody UpdateSharePermissionRequest request) {

        NoteShareResponse response = noteShareService.updatePermission(
                userDetail.getUser().getId(), noteId, sharedWithUserId, request);
        return ResponseEntity.ok(response);
    }

    /** Withdraw access entirely. 204 — the grant is gone, there's nothing left to return. */
    @DeleteMapping("/{sharedWithUserId}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId,
            @PathVariable Long sharedWithUserId) {

        noteShareService.revoke(userDetail.getUser().getId(), noteId, sharedWithUserId);
        return ResponseEntity.noContent().build();
    }
}