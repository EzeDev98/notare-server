package com.notare.feature.transcription.web;

import com.notare.feature.transcription.dto.response.TranscriptionResponse;
import com.notare.feature.transcription.service.TranscriptionService;
import com.notare.feature.user.model.UserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * A transcription is one-to-one with a voice note, so it's modeled as a
 * sub-resource of the voice note: /voice-notes/{voiceNoteId}/transcription.
 * The controller lives in the transcription module even though its URL nests
 * under voice-notes — URL shape and module ownership are allowed to differ.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/voice-notes/{voiceNoteId}/transcription")
public class TranscriptionController {

    private final TranscriptionService transcriptionService;

    /**
     * Submit a transcription job. Returns 202 Accepted — the correct code for
     * "request accepted, processing will happen asynchronously". The body holds
     * the current (initially PENDING) state, and the Location header points at
     * the status endpoint the client should poll. Idempotent: re-submitting just
     * returns the existing job's state.
     */
    @PostMapping
    public ResponseEntity<TranscriptionResponse> submit(@AuthenticationPrincipal UserDetail userDetail, @PathVariable Long voiceNoteId) {

        TranscriptionResponse response = transcriptionService.submit(userDetail.getUser().getId(), voiceNoteId);

        // The poll URL is this same path via GET.
        URI pollLocation = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.accepted().location(pollLocation).body(response);
    }

    /**
     * Poll the transcription status/result for this voice note. 404 if no job
     * has been submitted for it yet.
     */
    @GetMapping
    public ResponseEntity<TranscriptionResponse> getStatus(@AuthenticationPrincipal UserDetail userDetail, @PathVariable Long voiceNoteId) {

        TranscriptionResponse response = transcriptionService.getByVoiceNote(userDetail.getUser().getId(), voiceNoteId);
        return ResponseEntity.ok(response);
    }
}