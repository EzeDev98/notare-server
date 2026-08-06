package com.notare.feature.transcription.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.transcription.dto.response.TranscriptionResponse;
import org.springframework.data.domain.Pageable;

public interface TranscriptionService {

    /**
     * Submit (or re-submit) a transcription job for a voice note. Idempotent: if
     * a job is already pending, processing, or completed, the current state is
     * returned and nothing new starts; a previously failed job is retried.
     */
    TranscriptionResponse submit(Long ownerId, Long voiceNoteId);

    /** Poll the transcription status/result for a voice note. */
    TranscriptionResponse getByVoiceNote(Long ownerId, Long voiceNoteId);

    /** Keyword search over the owner's completed transcripts, paginated. */
    PagedResponse<TranscriptionResponse> searchTranscripts(Long ownerId, String query, Pageable pageable);
}