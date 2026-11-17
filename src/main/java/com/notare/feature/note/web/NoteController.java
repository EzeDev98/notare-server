package com.notare.feature.note.web;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.CreateNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.note.service.NoteCreation;
import com.notare.feature.note.service.NoteService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/notes")
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    public ResponseEntity<NoteResponse> createNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @Valid @RequestBody CreateNoteRequest request) {

        Long ownerId = userDetail.getUser().getId();
        NoteCreation creation = request.getClientId() == null
                ? new NoteCreation(noteService.createNote(ownerId, request), true)
                : noteService.createIdempotent(ownerId, request, request.getClientId());
        NoteResponse response = creation.note();

        // The same clientId again returns the note it already made: 200, nothing new created.
        if (!creation.created()) {
            return ResponseEntity.ok(response);
        }

        // 201 Created with a Location header pointing at the new resource.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{noteId}")
    public ResponseEntity<NoteResponse> getNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId) {

        NoteResponse response = noteService.getNote(userDetail.getUser().getId(), noteId);
        return ResponseEntity.ok(response);
    }

    /**
     * The owner's notes.
     *
     * <p>The default sort has always been {@code pinned,updatedAt} DESC, which
     * only started meaning anything once pinning could be set — before this
     * change it ordered every row by a column permanently false. Pinned notes
     * now rise to the top, which is the entire user-visible point of the
     * feature.
     */
    @GetMapping
    public ResponseEntity<PagedResponse<NoteSummaryResponse>> listNotes(
            @AuthenticationPrincipal UserDetail userDetail,
            @ParameterObject
            @PageableDefault(size = 20, sort = {"pinned", "updatedAt"}, direction = Sort.Direction.DESC)
            Pageable pageable) {

        PagedResponse<NoteSummaryResponse> response =
                noteService.listNotes(userDetail.getUser().getId(), pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Keyword search over the owner's active notes' content, partial and
     * case-insensitive, paginated. The literal path /notes/search takes
     * precedence over /notes/{noteId}, so there's no clash with get-by-id.
     */
    @GetMapping("/search")
    public ResponseEntity<PagedResponse<NoteSummaryResponse>> searchNotes(
            @AuthenticationPrincipal UserDetail userDetail,
            @RequestParam("q") String query,
            @ParameterObject
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        PagedResponse<NoteSummaryResponse> response =
                noteService.searchNotes(userDetail.getUser().getId(), query, pageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{noteId}")
    public ResponseEntity<NoteResponse> updateNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId,
            @Valid @RequestBody UpdateNoteRequest request) {

        NoteResponse response = noteService.updateNote(userDetail.getUser().getId(), noteId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Auto-save: a partial update the client calls frequently while the user
     * types. PATCH is the right verb — it modifies only the fields provided,
     * leaving the rest untouched.
     */
    @PatchMapping("/{noteId}")
    public ResponseEntity<NoteResponse> autoSave(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId,
            @Valid @RequestBody AutoSaveNoteRequest request) {

        NoteResponse response = noteService.autoSave(userDetail.getUser().getId(), noteId, request);
        return ResponseEntity.ok(response);
    }

    /** Deletes the note for good, with its voice clips. There is no trash and no restore. */
    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId) {

        noteService.permanentlyDeleteNote(userDetail.getUser().getId(), noteId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Pin: POST to set, DELETE to clear.
     *
     * <h2>Why action endpoints and not a field on PATCH</h2>
     *
     * <p>PATCH /notes/{id} is auto-save. It carries content, requires
     * a version, and fires on a debounce while someone is typing. Putting
     * organisation flags in that body would mean every keystroke's save also
     * asserts a pin state — so an editor opened before the user pinned the note
     * from the list would silently un-pin it on the next save. A write nobody
     * asked for, arriving as a side effect of typing something unrelated.
     *
     * <p>Separate verbs also make the operations idempotent in the HTTP sense
     * rather than only by accident: POST /pin twice leaves the note pinned, and
     * DELETE /pin on an unpinned note is a no-op that returns the note as-is.
     * Same reasoning as /collaborations/{id}/accept — an action sub-path rather
     * than a general-purpose write to a status field, because the general write
     * hands clients the ability to set anything.
     *
     * <p>Both return the full note, including its new version, so a client
     * can adopt it without a refetch. See saveOrganisationChange in the service
     * for why these bump the version at all.
     *
     * <p><b>Owner only, and there is deliberately no /shared-notes
     * counterpart.</b> Step 7 scoped WRITE to content: pin is
     * the owner's private view of their own note, and a
     * collaborator reorganising someone else's sidebar is not a feature. No
     * check is needed to enforce that — these paths live under the
     * owner-scoped /notes collection and the service looks the note up by
     * owner.
     */
    @PostMapping("/{noteId}/pin")
    public ResponseEntity<NoteResponse> pinNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId) {

        return ResponseEntity.ok(noteService.setPinned(userDetail.getUser().getId(), noteId, true));
    }

    @DeleteMapping("/{noteId}/pin")
    public ResponseEntity<NoteResponse> unpinNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId) {

        return ResponseEntity.ok(noteService.setPinned(userDetail.getUser().getId(), noteId, false));
    }
}