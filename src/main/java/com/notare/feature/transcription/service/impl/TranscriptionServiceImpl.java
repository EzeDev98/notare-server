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

/** Requests, reads and searches transcriptions. */
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
        Long ownerId = authorise(userId, voiceNoteId);

        Transcription transcription = transcriptionRepository
                .findByVoiceNoteIdAndOwnerId(voiceNoteId, ownerId)
                .orElse(null);

        if (transcription != null) {
            switch (transcription.getStatus()) {
                case PENDING, PROCESSING, COMPLETED -> {
                    return transcriptionMapper.toResponse(transcription);
                }
                case FAILED -> resetToPending(transcription, userId);
            }
        } else {
            transcription = createPending(ownerId, userId, voiceNoteId);
        }

        Transcription saved = transcriptionRepository.save(transcription);
        voiceNoteService.touchClip(voiceNoteId);

        eventPublisher.publishEvent(new TranscriptionRequestedEvent(saved.getId()));

        return transcriptionMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TranscriptionResponse getByVoiceNote(Long userId, Long voiceNoteId) {
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
        String term = query == null ? "" : query.strip();
        Page<Transcription> page = transcriptionRepository.searchByOwnerAndStatus(
                ownerId, TranscriptionStatus.COMPLETED, LikeEscaper.escape(term), pageable);
        List<TranscriptionResponse> content = page.getContent().stream()
                .map(transcriptionMapper::toResponse)
                .toList();
        return PagedResponse.of(content, page);
    }

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
