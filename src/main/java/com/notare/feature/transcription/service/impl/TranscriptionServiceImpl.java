package com.notare.feature.transcription.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.TranscriptionNotFoundException;
import com.notare.common.util.LikeEscaper;
import com.notare.feature.transcription.dto.response.TranscriptionResponse;
import com.notare.feature.transcription.enums.TranscriptionStatus;
import com.notare.feature.transcription.event.TranscriptionRequestedEvent;
import com.notare.feature.transcription.mapper.TranscriptionMapper;
import com.notare.feature.transcription.model.Transcription;
import com.notare.feature.transcription.repository.TranscriptionRepository;
import com.notare.feature.transcription.service.TranscriptionService;
import com.notare.feature.voicenote.service.VoiceNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TranscriptionServiceImpl implements TranscriptionService {

    private final TranscriptionRepository transcriptionRepository;
    private final TranscriptionMapper transcriptionMapper;
    private final VoiceNoteService voiceNoteService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public TranscriptionResponse submit(Long userId, Long voiceNoteId) {
        // The note's owner and anyone with WRITE on it may ask (step 17d, B-11). The job belongs to the
        // clip's owner, so the audio can be found; the asker is remembered for the notice that tells
        // them the transcript is ready. Both checks go through other modules' PUBLIC services.
        Long ownerId = authorise(userId, voiceNoteId);

        Transcription transcription = transcriptionRepository
                .findByVoiceNoteIdAndOwnerId(voiceNoteId, ownerId)
                .orElse(null);

        if (transcription != null) {
            switch (transcription.getStatus()) {
                case PENDING, PROCESSING, COMPLETED -> {
                    // Already queued, running, or done — idempotent: return the
                    // current state and start nothing new.
                    return transcriptionMapper.toResponse(transcription);
                }
                case FAILED -> resetToPending(transcription, userId);
            }
        } else {
            transcription = createPending(ownerId, userId, voiceNoteId);
        }

        Transcription saved = transcriptionRepository.save(transcription);
        // The clip's next sync carries the new status (step 17c). Same transaction as the save.
        voiceNoteService.touchClip(voiceNoteId);

        // Publish INSIDE this transaction. The listener consumes it AFTER_COMMIT,
        // so the async worker never queries for this row before it's committed.
        eventPublisher.publishEvent(new TranscriptionRequestedEvent(saved.getId()));

        return transcriptionMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TranscriptionResponse getByVoiceNote(Long userId, Long voiceNoteId) {
        // Anyone who can see the note may read the transcript, Viewers included.
        Long ownerId = authorise(userId, voiceNoteId);
        Transcription transcription = transcriptionRepository
                .findByVoiceNoteIdAndOwnerId(voiceNoteId, ownerId)
                .orElseThrow(() -> new TranscriptionNotFoundException(
                        "No transcription found for this voice note."));
        return transcriptionMapper.toResponse(transcription);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, TranscriptionResponse> getForVoiceNotes(Collection<Long> voiceNoteIds) {
        if (voiceNoteIds == null || voiceNoteIds.isEmpty()) {
            return Map.of();
        }
        return transcriptionRepository.findByVoiceNoteIdIn(voiceNoteIds).stream()
                .collect(Collectors.toMap(Transcription::getVoiceNoteId, transcriptionMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TranscriptionResponse> searchTranscripts(Long ownerId, String query, Pageable pageable) {
        // Same normalization as note search: null-safe, trimmed; blank term
        // matches all completed transcripts.
        String term = query == null ? "" : query.strip();
        Page<Transcription> page = transcriptionRepository.searchByOwnerAndStatus(
                ownerId, TranscriptionStatus.COMPLETED, LikeEscaper.escape(term), pageable);
        List<TranscriptionResponse> content = page.getContent().stream()
                .map(transcriptionMapper::toResponse)
                .toList();
        return PagedResponse.of(content, page);
    }

    /**
     * Checks that the clip is this user's and returns its owner. Anyone else gets the same 404 as a
     * clip that does not exist.
     */
    private Long authorise(Long userId, Long voiceNoteId) {
        return voiceNoteService.getVoiceNote(userId, voiceNoteId).ownerId();
    }

    private Transcription createPending(Long ownerId, Long requestedBy, Long voiceNoteId) {
        Transcription t = new Transcription();
        t.setOwnerId(ownerId);
        t.setRequestedBy(requestedBy);
        t.setVoiceNoteId(voiceNoteId);
        t.setStatus(TranscriptionStatus.PENDING);
        return t;
    }

    private void resetToPending(Transcription t, Long requestedBy) {
        t.setStatus(TranscriptionStatus.PENDING);
        t.setRequestedBy(requestedBy);
        t.setErrorMessage(null);
        t.setTranscript(null);
        t.setConfidence(null);
        t.setLanguage(null);
    }
}