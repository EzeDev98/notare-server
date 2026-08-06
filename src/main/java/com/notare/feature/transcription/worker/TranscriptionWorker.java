package com.notare.feature.transcription.worker;

import com.notare.feature.transcription.dto.response.TranscriptionResult;
import com.notare.feature.transcription.engine.TranscriptionEngine;
import com.notare.feature.transcription.enums.TranscriptionStatus;
import com.notare.feature.transcription.event.TranscriptionRequestedEvent;
import com.notare.feature.transcription.model.Transcription;
import com.notare.feature.transcription.repository.TranscriptionRepository;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.service.VoiceNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranscriptionWorker {

    private final TranscriptionRepository transcriptionRepository;
    private final VoiceNoteService voiceNoteService;
    private final TranscriptionEngine transcriptionEngine;

    /**
     * Runs the job. Three annotations, each doing one job:
     *  - @TransactionalEventListener(AFTER_COMMIT): fires only after the submit
     *    transaction commits, so the PENDING row is guaranteed visible here.
     *  - @Async("transcriptionExecutor"): runs on the dedicated pool, off the
     *    request thread — submit already returned to the client.
     *  - separate bean: because this is invoked by Spring's event machinery
     *    (not a self-call), the @Async proxy actually applies. A self-invocation
     *    would silently run synchronously.
     */
    @Async("transcriptionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTranscriptionRequested(TranscriptionRequestedEvent event) {
        process(event.transcriptionId());
    }

    private void process(Long transcriptionId) {
        Transcription transcription = transcriptionRepository.findById(transcriptionId).orElse(null);
        if (transcription == null) {
            // Row was deleted between submit and now — nothing to do.
            return;
        }

        try {
            // Short write #1: flip to PROCESSING so a polling client sees movement.
            transcription.setStatus(TranscriptionStatus.PROCESSING);
            transcriptionRepository.save(transcription);

            // The slow part — deliberately OUTSIDE any transaction, so we don't
            // hold a DB connection open for the whole engine call.
            VoiceNoteContent audio = voiceNoteService.loadAudio(transcription.getOwnerId(), transcription.getVoiceNoteId());
            TranscriptionResult result = transcriptionEngine.transcribe(audio.resource(), audio.contentType());

            // Short write #2: store the result and mark COMPLETED.
            transcription.setTranscript(result.transcript());
            transcription.setConfidence(result.confidence());
            transcription.setLanguage(result.language());
            transcription.setErrorMessage(null);
            transcription.setStatus(TranscriptionStatus.COMPLETED);
            transcriptionRepository.save(transcription);

        } catch (Exception ex) {
            log.warn("Transcription {} failed", transcriptionId, ex);
            markFailed(transcriptionId, ex);
        }
    }

    private void markFailed(Long transcriptionId, Exception ex) {
        // Reload fresh: after a failure the in-memory entity may be in a partial
        // state, so we re-read and set only the failure fields.
        transcriptionRepository.findById(transcriptionId).ifPresent(transcription -> {
            transcription.setStatus(TranscriptionStatus.FAILED);
            transcription.setErrorMessage(ex.getMessage());
            transcriptionRepository.save(transcription);
        });
    }
}