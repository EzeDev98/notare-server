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

/** Endpoints for a user's notes. */
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

        if (!creation.created()) {
            return ResponseEntity.ok(response);
        }

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

    @PatchMapping("/{noteId}")
    public ResponseEntity<NoteResponse> autoSave(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId,
            @Valid @RequestBody AutoSaveNoteRequest request) {

        NoteResponse response = noteService.autoSave(userDetail.getUser().getId(), noteId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId) {

        noteService.permanentlyDeleteNote(userDetail.getUser().getId(), noteId);
        return ResponseEntity.noContent().build();
    }

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

    @PostMapping("/{noteId}/restore-original")
    public ResponseEntity<NoteResponse> restoreOriginal(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId) {

        return ResponseEntity.ok(noteService.restoreOriginal(userDetail.getUser().getId(), noteId));
    }
}
