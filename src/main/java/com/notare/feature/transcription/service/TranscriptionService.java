package com.notare.feature.transcription.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.transcription.dto.response.TranscriptionResponse;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;

public interface TranscriptionService {

    /**
     * Who may ask (step 17d, B-11): the note's owner and anyone with WRITE on it, 403 for a Viewer, 404
     * for anyone with no access. Reading a transcript is open to Viewers too.
     *
     * <p>Submit (or re-submit) a transcription job for a voice note. Idempotent: if
     * a job is already pending, processing, or completed, the current state is
     * returned and nothing new starts; a previously failed job is retried.
     */
    TranscriptionResponse submit(Long userId, Long voiceNoteId);

    /** Poll the transcription status/result for a voice note. */
    TranscriptionResponse getByVoiceNote(Long userId, Long voiceNoteId);

    /**
     * The transcriptions of a batch of voice notes, keyed by voice note id; a clip with none is absent.
     * No ownership check: the caller (the sync pull) passes only the ids of the caller's own clips.
     */
    Map<Long, TranscriptionResponse> getForVoiceNotes(Collection<Long> voiceNoteIds);

    /** Keyword search over the owner's completed transcripts, paginated. */
    PagedResponse<TranscriptionResponse> searchTranscripts(Long ownerId, String query, Pageable pageable);
}