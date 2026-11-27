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

/** Endpoints for requesting and reading a voice clip's transcription. */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/voice-notes/{voiceNoteId}/transcription")
public class TranscriptionController {

    private final TranscriptionService transcriptionService;

    @PostMapping
    public ResponseEntity<TranscriptionResponse> submit(@AuthenticationPrincipal UserDetail userDetail, @PathVariable Long voiceNoteId) {

        TranscriptionResponse response = transcriptionService.submit(userDetail.getUser().getId(), voiceNoteId);

        URI pollLocation = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.accepted().location(pollLocation).body(response);
    }

    @GetMapping
    public ResponseEntity<TranscriptionResponse> getStatus(@AuthenticationPrincipal UserDetail userDetail, @PathVariable Long voiceNoteId) {

        TranscriptionResponse response = transcriptionService.getByVoiceNote(userDetail.getUser().getId(), voiceNoteId);
        return ResponseEntity.ok(response);
    }
}
