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

import java.util.List;

@Service
@RequiredArgsConstructor
public class TranscriptionServiceImpl implements TranscriptionService {

    private final TranscriptionRepository transcriptionRepository;
    private final TranscriptionMapper transcriptionMapper;
    private final VoiceNoteService voiceNoteService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public TranscriptionResponse submit(Long ownerId, Long voiceNoteId) {
        // Cross-module ownership/existence check through the voicenote module's
        // PUBLIC service (not its repository). Throws VoiceNoteNotFound (404) if
        // the voice note isn't the caller's. This is the clean Spring Modulith
        // way for one module to consult another — via its API, not its internals.
        voiceNoteService.getVoiceNote(ownerId, voiceNoteId);

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
                case FAILED -> resetToPending(transcription);
            }
        } else {
            transcription = createPending(ownerId, voiceNoteId);
        }

        Transcription saved = transcriptionRepository.save(transcription);

        // Publish INSIDE this transaction. The listener consumes it AFTER_COMMIT,
        // so the async worker never queries for this row before it's committed.
        eventPublisher.publishEvent(new TranscriptionRequestedEvent(saved.getId()));

        return transcriptionMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TranscriptionResponse getByVoiceNote(Long ownerId, Long voiceNoteId) {
        Transcription transcription = transcriptionRepository
                .findByVoiceNoteIdAndOwnerId(voiceNoteId, ownerId)
                .orElseThrow(() -> new TranscriptionNotFoundException(
                        "No transcription found for this voice note."));
        return transcriptionMapper.toResponse(transcription);
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

    private Transcription createPending(Long ownerId, Long voiceNoteId) {
        Transcription t = new Transcription();
        t.setOwnerId(ownerId);
        t.setVoiceNoteId(voiceNoteId);
        t.setStatus(TranscriptionStatus.PENDING);
        return t;
    }

    private void resetToPending(Transcription t) {
        t.setStatus(TranscriptionStatus.PENDING);
        t.setErrorMessage(null);
        t.setTranscript(null);
        t.setConfidence(null);
        t.setLanguage(null);
    }
}