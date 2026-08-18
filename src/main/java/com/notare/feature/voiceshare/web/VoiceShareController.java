package com.notare.feature.voiceshare.web;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.voiceshare.dto.request.ShareVoiceNoteRequest;
import com.notare.feature.voiceshare.dto.request.UpdateVoiceSharePermissionRequest;
import com.notare.feature.voiceshare.dto.response.VoiceShareResponse;
import com.notare.feature.voiceshare.service.VoiceShareService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * The owner's control panel for one voice note's access list.
 *
 * <p>Mirrors {@link com.notare.feature.share.web.NoteShareController} exactly
 * — nested under the voice note (a grant has no meaning apart from the voice
 * note it's on), addressed by RECIPIENT id rather than share id, protected by
 * the default authenticated() rule.
 *
 * <p>No clash with VoiceNoteController: it owns /voice-notes and
 * /voice-notes/{id} plus its stream/download sub-paths, and this owns
 * /voice-notes/{id}/shares and below.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/voice-notes/{voiceNoteId}/shares")
public class VoiceShareController {

    private final VoiceShareService voiceShareService;

    /**
     * Grant access. 201 with a Location pointing at the grant's own address.
     *
     * <p>Service-side failures: sharing with yourself or with someone who
     * isn't an accepted collaborator -> 400; not your voice note -> 404;
     * already shared -> 409 (change the level with PUT rather than
     * re-POSTing).
     */
    @PostMapping
    public ResponseEntity<VoiceShareResponse> shareVoiceNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long voiceNoteId,
            @Valid @RequestBody ShareVoiceNoteRequest request) {

        VoiceShareResponse response = voiceShareService.shareVoiceNote(
                userDetail.getUser().getId(), voiceNoteId, request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sharedWithUserId}")
                .buildAndExpand(response.sharedWithUserId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /** Who currently has access to this voice note. Owner-scoped; someone else's voice note is a 404, not an empty page. */
    @GetMapping
    public ResponseEntity<PagedResponse<VoiceShareResponse>> listShares(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long voiceNoteId,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        PagedResponse<VoiceShareResponse> response =
                voiceShareService.listShares(userDetail.getUser().getId(), voiceNoteId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Promote or demote an existing grant. PUT, not PATCH — same reasoning as
     * NoteShareController: the grant has exactly one mutable field, so the
     * body IS the complete editable state. Idempotent — setting the level it
     * already has returns 200 unchanged.
     */
    @PutMapping("/{sharedWithUserId}")
    public ResponseEntity<VoiceShareResponse> updatePermission(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long voiceNoteId,
            @PathVariable Long sharedWithUserId,
            @Valid @RequestBody UpdateVoiceSharePermissionRequest request) {

        VoiceShareResponse response = voiceShareService.updatePermission(
                userDetail.getUser().getId(), voiceNoteId, sharedWithUserId, request);
        return ResponseEntity.ok(response);
    }

    /** Withdraw access entirely. 204 — the grant is gone, there's nothing left to return. */
    @DeleteMapping("/{sharedWithUserId}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long voiceNoteId,
            @PathVariable Long sharedWithUserId) {

        voiceShareService.revoke(userDetail.getUser().getId(), voiceNoteId, sharedWithUserId);
        return ResponseEntity.noContent().build();
    }
}
