package com.notare.feature.voiceshare.web;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voiceshare.dto.response.SharedVoiceNoteSummaryResponse;
import com.notare.feature.voiceshare.service.SharedVoiceNoteService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "Shared with me" for voice notes — the recipient's view. Mirrors
 * {@link com.notare.feature.share.web.SharedNoteController}'s framing exactly:
 * a separate collection from /voice-notes (which stays strictly owner-only),
 * addressed by voice note id rather than share id, with the same
 * no-grant-is-404 / grant-too-weak-is-403 authorization story.
 *
 * <p>The stream/download endpoints below are VoiceNoteController's Range-
 * request logic verbatim, gated through {@link SharedVoiceNoteService#getAudio}
 * (a grant check) instead of {@code VoiceNoteService#loadAudio} (an ownership
 * check) — same CHUNK_SIZE, same single-range handling, same reasoning for
 * serving through the controller rather than a static resource.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/shared-voice-notes")
public class SharedVoiceNoteController {

    /** Max bytes returned per streamed range response (1 MB); the player re-requests the next slice. */
    private static final long CHUNK_SIZE = 1024 * 1024;

    private final SharedVoiceNoteService sharedVoiceNoteService;

    /** Voice notes other people have shared with me. Sorted by when the voice note was shared, newest first — same grant-column-only sort caveat as SharedNoteController. */
    @GetMapping
    public ResponseEntity<PagedResponse<SharedVoiceNoteSummaryResponse>> listSharedWithMe(
            @AuthenticationPrincipal UserDetail userDetail,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        PagedResponse<SharedVoiceNoteSummaryResponse> response =
                sharedVoiceNoteService.listSharedWithMe(userDetail.getUser().getId(), pageable);
        return ResponseEntity.ok(response);
    }

    /** One shared voice note's metadata in full. Requires at least READ. */
    @GetMapping("/{voiceNoteId}")
    public ResponseEntity<SharedVoiceNoteSummaryResponse> getSharedVoiceNote(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long voiceNoteId) {

        SharedVoiceNoteSummaryResponse response =
                sharedVoiceNoteService.getSharedVoiceNote(userDetail.getUser().getId(), voiceNoteId);
        return ResponseEntity.ok(response);
    }

    /**
     * Download the whole file as an attachment. Same Content-Disposition:
     * attachment behavior as VoiceNoteController#download.
     */
    @GetMapping("/{voiceNoteId}/download")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long voiceNoteId) {

        VoiceNoteContent content = sharedVoiceNoteService.getAudio(userDetail.getUser().getId(), voiceNoteId);
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
     * Stream with HTTP Range support so an audio player can seek. Identical
     * logic to VoiceNoteController#stream — the only difference is that
     * access comes from a grant, resolved inside
     * SharedVoiceNoteService#getAudio, rather than ownership.
     */
    @GetMapping("/{voiceNoteId}/stream")
    public ResponseEntity<ResourceRegion> stream(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long voiceNoteId,
            @RequestHeader HttpHeaders headers) {

        VoiceNoteContent content = sharedVoiceNoteService.getAudio(userDetail.getUser().getId(), voiceNoteId);
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
