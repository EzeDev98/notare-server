package com.notare.feature.share.web;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.share.dto.response.SharedNoteSummaryResponse;
import com.notare.feature.share.service.SharedNoteService;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

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
     * Injected rather than hardcoded, so the upload endpoint's Location
     * header stays correct if app.title (the API's base path) ever changes
     * in application.yml/properties — same value every other endpoint's
     * @RequestMapping resolves at startup, just fetched by hand here since
     * this one Location has to be built manually rather than declared on
     * an annotation.
     */
    @org.springframework.beans.factory.annotation.Value("${app.title}")
    private String appTitle;

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

    /** Max bytes returned per streamed range response (1 MB), same as VoiceNoteController and SharedVoiceNoteController. */
    private static final long CHUNK_SIZE = 1024 * 1024;

    /**
     * Every voice note attached to this shared note, regardless of which
     * collaborator recorded it. Requires at least READ.
     */
    @GetMapping("/{noteId}/voice-notes")
    public ResponseEntity<List<VoiceNoteResponse>> listAttachedVoiceNotes(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId) {

        List<VoiceNoteResponse> response =
                sharedNoteService.listAttachedVoiceNotes(userDetail.getUser().getId(), noteId);
        return ResponseEntity.ok(response);
    }

    /**
     * Record a new voice note directly onto this shared note. Requires WRITE;
     * a READ recipient gets 403 — same as any other edit to the note.
     */
    @PostMapping(value = "/{noteId}/voice-notes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VoiceNoteResponse> uploadVoiceNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds) {

        VoiceNoteResponse response = sharedNoteService.uploadVoiceNote(
                userDetail.getUser().getId(), noteId, file, durationSeconds);

        // fromCurrentContextPath + the injected app.title, not
        // fromCurrentRequest + a literal "${app.title}" placeholder — Spring
        // only resolves ${...} property placeholders in @RequestMapping/@Value,
        // never inside a string handed to UriComponentsBuilder at runtime. The
        // earlier version would have shipped a Location header containing the
        // literal, unresolved text "${app.title}".
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(appTitle)
                .path("/shared-voice-notes/{voiceNoteId}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Download an attached voice note's audio. Gated by the PARENT NOTE's
     * grant (see SharedNoteService#getAttachedVoiceNoteAudio) — deliberately
     * NOT the same authorization path as SharedVoiceNoteController, which
     * gates on a VoiceShare grant for a directly-shared voice note.
     */
    @GetMapping("/{noteId}/voice-notes/{voiceNoteId}/download")
    public ResponseEntity<Resource> downloadAttachedVoiceNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId,
            @PathVariable Long voiceNoteId) {

        VoiceNoteContent content = sharedNoteService.getAttachedVoiceNoteAudio(
                userDetail.getUser().getId(), noteId, voiceNoteId);
        String filename = content.originalFilename() != null
                ? content.originalFilename()
                : "voice-note-" + voiceNoteId;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(content.resource());
    }

    /**
     * Stream an attached voice note with HTTP Range support. Identical Range
     * logic to VoiceNoteController#stream and SharedVoiceNoteController#stream
     * — only the authorization path differs.
     */
    @GetMapping("/{noteId}/voice-notes/{voiceNoteId}/stream")
    public ResponseEntity<ResourceRegion> streamAttachedVoiceNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId,
            @PathVariable Long voiceNoteId,
            @RequestHeader HttpHeaders headers) {

        VoiceNoteContent content = sharedNoteService.getAttachedVoiceNoteAudio(
                userDetail.getUser().getId(), noteId, voiceNoteId);
        Resource audio = content.resource();
        long contentLength = content.sizeBytes();
        MediaType mediaType = MediaType.parseMediaType(content.contentType());

        List<HttpRange> ranges = headers.getRange();

        if (ranges.isEmpty()) {
            ResourceRegion whole = new ResourceRegion(audio, 0, contentLength);
            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentType(mediaType)
                    .body(whole);
        }

        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);
        long sliceLength = Math.min(CHUNK_SIZE, end - start + 1);
        ResourceRegion region = new ResourceRegion(audio, start, sliceLength);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(mediaType)
                .body(region);
    }
}