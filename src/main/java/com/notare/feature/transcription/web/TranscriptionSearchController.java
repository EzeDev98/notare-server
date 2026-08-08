package com.notare.feature.transcription.web;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.transcription.dto.response.TranscriptionResponse;
import com.notare.feature.transcription.service.TranscriptionService;
import com.notare.feature.user.model.UserDetail;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Search across the whole transcriptions collection, so it's a top-level
 * resource (/transcriptions) rather than nested under a single voice note. The
 * same entity legitimately appears both as a scoped sub-resource
 * (/voice-notes/{id}/transcription) and as a searchable collection here — a
 * common REST shape.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/transcriptions")
public class TranscriptionSearchController {

    private final TranscriptionService transcriptionService;

    @GetMapping("/search")
    public ResponseEntity<PagedResponse<TranscriptionResponse>> search(
            @AuthenticationPrincipal UserDetail userDetail,
            @RequestParam("query") String query,
            @ParameterObject
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(transcriptionService.searchTranscripts(userDetail.getUser().getId(), query, pageable));
    }
}