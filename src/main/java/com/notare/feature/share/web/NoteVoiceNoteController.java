package com.notare.feature.share.web;

import com.notare.common.exception.InvalidVoiceUploadException;
import com.notare.feature.share.service.SharedNoteService;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.dto.response.VoiceUpload;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * A voice clip as a child of its note (step 17b, B-09, P-02): {@code POST /notes/{noteId}/voice-notes}.
 * It serves the note's owner and anyone with WRITE, and replaces the two older uploads
 * ({@code POST /voice-notes} and {@code POST /shared-notes/{noteId}/voice-notes}).
 *
 * <p>It lives in the share module because only that module can tell an owner from an Editor
 * without the voicenote module depending on shares. The clip's {@code clientId} is required and
 * makes the upload idempotent: the same one again is a 200 with the stored clip, a new clip a 201.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/notes/{noteId}/voice-notes")
public class NoteVoiceNoteController {

    private static final int CLIENT_ID_MAX = 64;

    private final SharedNoteService sharedNoteService;

    @org.springframework.beans.factory.annotation.Value("${app.title}")
    private String appTitle;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VoiceNoteResponse> upload(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long noteId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("clientId") String clientId,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds) {

        if (clientId.isBlank() || clientId.length() > CLIENT_ID_MAX) {
            throw new InvalidVoiceUploadException("clientId must be 1 to " + CLIENT_ID_MAX + " characters.");
        }

        VoiceUpload upload = sharedNoteService.uploadVoiceNoteToNote(
                userDetail.getUser().getId(), noteId, file, clientId, durationSeconds);

        if (!upload.created()) {
            return ResponseEntity.ok(upload.clip());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(appTitle)
                .path("/voice-notes/{voiceNoteId}")
                .buildAndExpand(upload.clip().id())
                .toUri();
        return ResponseEntity.created(location).body(upload.clip());
    }
}
