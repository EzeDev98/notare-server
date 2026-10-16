package com.notare.feature.share.web;

import com.notare.feature.share.dto.request.CopyVoiceNoteRequest;
import com.notare.feature.share.service.SharedNoteService;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.dto.response.VoiceUpload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * POST /voice-notes/{id}/copy (step 17f, B-05): "Make a copy" of a shared note needs its clips
 * copied too. Lives in the share module because deciding who may read the source is the share
 * module's job. 201 with the new clip, or 200 with the existing one on a repeat of the same clientId.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/voice-notes/{voiceNoteId}/copy")
public class VoiceNoteCopyController {

    private final SharedNoteService sharedNoteService;

    @org.springframework.beans.factory.annotation.Value("${app.title}")
    private String appTitle;

    @PostMapping
    public ResponseEntity<VoiceNoteResponse> copy(
            @AuthenticationPrincipal UserDetail userDetail,
            @PathVariable Long voiceNoteId,
            @Valid @RequestBody CopyVoiceNoteRequest request) {

        VoiceUpload result = sharedNoteService.copyVoiceNote(
                userDetail.getUser().getId(), voiceNoteId, request.getTargetNoteId(), request.getClientId());

        if (!result.created()) {
            return ResponseEntity.ok(result.clip());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(appTitle)
                .path("/voice-notes/{voiceNoteId}")
                .buildAndExpand(result.clip().id())
                .toUri();
        return ResponseEntity.created(location).body(result.clip());
    }
}
