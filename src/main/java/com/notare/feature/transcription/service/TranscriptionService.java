package com.notare.feature.transcription.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.transcription.dto.response.TranscriptionResponse;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;

/** Requests, reads and searches transcriptions. */
public interface TranscriptionService {

    TranscriptionResponse submit(Long userId, Long voiceNoteId);

    TranscriptionResponse getByVoiceNote(Long userId, Long voiceNoteId);

    Map<Long, TranscriptionResponse> getForVoiceNotes(Collection<Long> voiceNoteIds);

    PagedResponse<TranscriptionResponse> searchTranscripts(Long ownerId, String query, Pageable pageable);
}
