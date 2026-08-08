package com.notare.feature.share.web;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.share.dto.response.SharedNoteSummaryResponse;
import com.notare.feature.share.service.SharedNoteService;
import com.notare.feature.user.model.UserDetail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Shared with me" — the recipient's view. This is the separate collection the
 * Step 7 design chose over resolving /notes/{id} for both owners and
 * collaborators: /notes stays strictly owner-only, and everything reachable
 * through a grant lives here.
 *
 * <p>The path variable is a NOTE id, not a share id, even though every request
 * resolves a grant first. The client thinks in notes; the grant is the
 * mechanism, not the thing being addressed. It also means a note has one stable
 * id across both collections, so /notes/7 and /shared-notes/7 are the same
 * note seen through different doors.
 *
 * <p>What the service returns on failure is the whole authorization story: no
 * grant -> 404 (indistinguishable from a note that doesn't exist, deliberately),
 * grant too weak for a write -> 403.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/shared-notes")
public class SharedNoteController {

    private final SharedNoteService sharedNoteService;

    /**
     * Notes other people have shared with me.
     *
     * <p>Sorted by when the note was shared, newest first. Worth knowing: the
     * sort can only use the GRANT's own columns, because the database query
     * runs against note_shares alone. "Most recently edited first" — which is
     * what people usually expect from an inbox like this — needs a join to the
     * notes table, and that's the same change that would fix the totalElements
     * count. Both are noted as hardening.
     */
    @GetMapping
    public ResponseEntity<PagedResponse<SharedNoteSummaryResponse>> listSharedWithMe(
            @AuthenticationPrincipal UserDetail userDetail,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        PagedResponse<SharedNoteSummaryResponse> response =
                sharedNoteService.listSharedWithMe(userDetail.getUser().getId(), pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Open a shared note in full. Requires at least READ.
     *
     * <p>Returns the same NoteResponse the owner's own endpoint returns —
     * including the version, which the client must echo back to save.
     */
    @GetMapping("/{noteId}")
    public ResponseEntity<NoteResponse> getSharedNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId) {

        NoteResponse response = sharedNoteService.getSharedNote(
                userDetail.getUser().getId(), noteId);
        return ResponseEntity.ok(response);
    }

    /**
     * Edit a shared note. Requires WRITE; a READ recipient gets 403.
     *
     * <p>Takes the same UpdateNoteRequest as the owner's PUT, version field and
     * all, and runs the same conflict check — so an owner and a collaborator
     * saving at once produces a 409 carrying the current version, and whoever
     * lost reloads and retries. Reusing the owner's DTO here is the point: one
     * request shape, one conflict protocol, one thing for the client to learn.
     *
     * <p>PATCH below is the partial counterpart, and the one a real editor
     * uses; this stays for an explicit save and for a client that wants full
     * replacement semantics.
     */
    @PutMapping("/{noteId}")
    public ResponseEntity<NoteResponse> updateSharedNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId,
            @Valid @RequestBody UpdateNoteRequest request) {

        NoteResponse response = sharedNoteService.updateSharedNote(
                userDetail.getUser().getId(), noteId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Auto-save a shared note. Requires WRITE; a READ recipient gets 403.
     *
     * <h2>Why this endpoint matters more than it looks</h2>
     *
     * <p>Without it, a WRITE collaborator had PUT and nothing else — meaning
     * their editor could only save on an explicit action or on blur, while the
     * owner's editor saved continuously. Two people editing the same note with
     * different save cadences is not a smaller version of collaborative
     * editing; it is a conflict generator. The collaborator accumulates minutes
     * of unsaved work, then sends it all at once against a version the owner
     * has moved past six times, and loses the race every time.
     *
     * <p>Partial semantics also matter here specifically. PUT sends the whole
     * note, so a collaborator saving a title change ships the entire content
     * body too — and if their view of that body is stale in any way, the full
     * replacement writes the stale copy back. PATCH sends only what changed.
     *
     * <p>Same AutoSaveNoteRequest as the owner's PATCH, same version field,
     * same conflict protocol. One request shape and one thing for the client to
     * learn, whichever collection the note came from.
     */
    @PatchMapping("/{noteId}")
    public ResponseEntity<NoteResponse> autoSaveSharedNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId,
            @Valid @RequestBody AutoSaveNoteRequest request) {

        NoteResponse response = sharedNoteService.autoSaveSharedNote(
                userDetail.getUser().getId(), noteId, request);
        return ResponseEntity.ok(response);
    }
}