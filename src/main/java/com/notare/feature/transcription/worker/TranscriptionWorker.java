package com.notare.feature.transcription.worker;

import com.notare.feature.transcription.dto.response.TranscriptionResult;
import com.notare.feature.transcription.engine.TranscriptionEngine;
import com.notare.feature.transcription.enums.TranscriptionStatus;
import com.notare.feature.transcription.event.TranscriptionCompletedEvent;
import com.notare.feature.transcription.event.TranscriptionFailedEvent;
import com.notare.feature.transcription.event.TranscriptionRequestedEvent;
import com.notare.feature.transcription.model.Transcription;
import com.notare.feature.transcription.repository.TranscriptionRepository;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.service.VoiceNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Runs transcription jobs in the background. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranscriptionWorker {

    private final TranscriptionRepository transcriptionRepository;
    private final VoiceNoteService voiceNoteService;
    private final TranscriptionEngine transcriptionEngine;
    private final ApplicationEventPublisher eventPublisher;

    @Async("transcriptionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTranscriptionRequested(TranscriptionRequestedEvent event) {
        process(event.transcriptionId());
    }

    private void process(Long transcriptionId) {
        Transcription transcription = transcriptionRepository.findById(transcriptionId).orElse(null);
        if (transcription == null) {
            return;
        }

        try {
            transcription.setStatus(TranscriptionStatus.PROCESSING);
            transcriptionRepository.save(transcription);
            voiceNoteService.touchClip(transcription.getVoiceNoteId());

            VoiceNoteContent audio = voiceNoteService.loadAudio(transcription.getOwnerId(), transcription.getVoiceNoteId());
            TranscriptionResult result = transcriptionEngine.transcribe(audio.resource(), audio.contentType());

            transcription.setTranscript(result.transcript());
            transcription.setConfidence(result.confidence());
            transcription.setLanguage(result.language());
            transcription.setErrorMessage(null);
            transcription.setStatus(TranscriptionStatus.COMPLETED);
            transcriptionRepository.save(transcription);
            voiceNoteService.touchClip(transcription.getVoiceNoteId());

            eventPublisher.publishEvent(new TranscriptionCompletedEvent(
                    transcription.getId(),
                    transcription.getVoiceNoteId(),
                    recipientOf(transcription)));

        } catch (Exception ex) {
            log.warn("Transcription {} failed", transcriptionId, ex);
            markFailed(transcriptionId, ex);
        }
    }

    private static Long recipientOf(Transcription transcription) {
        return transcription.getRequestedBy() != null ? transcription.getRequestedBy() : transcription.getOwnerId();
    }

    private void markFailed(Long transcriptionId, Exception ex) {
        transcriptionRepository.findById(transcriptionId).ifPresent(transcription -> {
            transcription.setStatus(TranscriptionStatus.FAILED);
            transcription.setErrorMessage(ex.getMessage());
            transcriptionRepository.save(transcription);
            voiceNoteService.touchClip(transcription.getVoiceNoteId());

            eventPublisher.publishEvent(new TranscriptionFailedEvent(
                    transcription.getId(),
                    transcription.getVoiceNoteId(),
                    recipientOf(transcription),
                    ex.getMessage()));
        });
    }
}
