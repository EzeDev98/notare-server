package com.notare.feature.voicenote.web;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.user.model.UserDetail;

import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/voice-notes")
public class VoiceNoteController {

    /** Max bytes returned per streamed range response (1 MB); the player re-requests the next slice. */
    private static final long CHUNK_SIZE = 1024 * 1024;

    private final VoiceNoteService voiceNoteService;

    /**
     * Multipart upload. `file` is the audio; `noteId` (optional) links it to a
     * text note; `durationSeconds` (optional) is the client-known duration.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VoiceNoteResponse> upload(
            @AuthenticationPrincipal UserDetail userDetail,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "noteId", required = false) Long noteId,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds) {

        VoiceNoteResponse response = voiceNoteService.upload(
                userDetail.getUser().getId(), file, noteId, durationSeconds);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{voiceNoteId}")
    public ResponseEntity<VoiceNoteResponse> getVoiceNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long voiceNoteId) {

        return ResponseEntity.ok(voiceNoteService.getVoiceNote(userDetail.getUser().getId(), voiceNoteId));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<VoiceNoteResponse>> listVoiceNotes(
            @AuthenticationPrincipal UserDetail userDetail,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(voiceNoteService.listVoiceNotes(userDetail.getUser().getId(), pageable));
    }

    /**
     * Voice notes attached to a specific text note. Literal "by-note" segment
     * outranks /{voiceNoteId}, so no routing clash. Returns a plain list (a note
     * has few attachments — no pagination needed).
     */
    @GetMapping("/by-note/{noteId}")
    public ResponseEntity<List<VoiceNoteResponse>> listForNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId) {

        return ResponseEntity.ok(voiceNoteService.listVoiceNotesForNote(userDetail.getUser().getId(), noteId));
    }

    /**
     * Download the whole file as an attachment (browser "save as"). Content-
     * Disposition: attachment is what makes the browser download rather than
     * try to render it.
     */
    @GetMapping("/{voiceNoteId}/download")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long voiceNoteId) {

        VoiceNoteContent content = voiceNoteService.loadAudio(userDetail.getUser().getId(), voiceNoteId);
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
     * Stream with HTTP Range support so an audio player can seek. A player asks
     * for byte ranges (e.g. "Range: bytes=500000-"); we answer 206 Partial
     * Content with just that slice. Serving through the controller (not as a
     * static resource) is what lets us keep the ownership check in front of it.
     */
    @GetMapping("/{voiceNoteId}/stream")
    public ResponseEntity<ResourceRegion> stream(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long voiceNoteId,
            @RequestHeader HttpHeaders headers) {

        VoiceNoteContent content = voiceNoteService.loadAudio(userDetail.getUser().getId(), voiceNoteId);
        Resource audio = content.resource();
        long contentLength = content.sizeBytes();
        MediaType mediaType = MediaType.parseMediaType(content.contentType());

        List<HttpRange> ranges = headers.getRange();

        if (ranges.isEmpty()) {
            // No Range header: serve the whole file, but advertise that ranges
            // are supported so the player knows it may seek.
            ResourceRegion whole = new ResourceRegion(audio, 0, contentLength);
            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentType(mediaType)
                    .body(whole);
        }

        // Honor the first requested range (single-range is enough for seeking).
        // Cap the slice at CHUNK_SIZE so one request can't pull the entire file;
        // the player re-requests the next slice as playback advances.
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);
        long sliceLength = Math.min(CHUNK_SIZE, end - start + 1);
        ResourceRegion region = new ResourceRegion(audio, start, sliceLength);

        // The ResourceRegion message converter fills in Content-Range and
        // Content-Length for the slice; we set the 206 status and media type.
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(mediaType)
                .body(region);
    }

    @DeleteMapping("/{voiceNoteId}")
    public ResponseEntity<Void> deleteVoiceNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long voiceNoteId) {

        voiceNoteService.deleteVoiceNote(userDetail.getUser().getId(), voiceNoteId);
        return ResponseEntity.noContent().build();
    }
}