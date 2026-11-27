package com.notare.feature.transcription.worker;

import com.notare.feature.transcription.enums.TranscriptionStatus;
import com.notare.feature.transcription.event.TranscriptionRequestedEvent;
import com.notare.feature.transcription.model.Transcription;
import com.notare.feature.transcription.repository.TranscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Re-queues transcriptions left unfinished, for example by a restart. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StuckTranscriptionRecovery {

    private static final long STALE_AFTER_MINUTES = 15;

    private final TranscriptionRepository transcriptionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void requeueOnStartup() {
        requeueStale();
    }

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT10M")
    @Transactional
    public void requeuePeriodically() {
        requeueStale();
    }

    private void requeueStale() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STALE_AFTER_MINUTES);
        List<Transcription> stuck = transcriptionRepository.findStale(
                List.of(TranscriptionStatus.PENDING, TranscriptionStatus.PROCESSING), cutoff);

        for (Transcription transcription : stuck) {
            transcription.setStatus(TranscriptionStatus.PENDING);
            transcriptionRepository.save(transcription);
            eventPublisher.publishEvent(new TranscriptionRequestedEvent(transcription.getId()));
        }
        if (!stuck.isEmpty()) {
            log.info("Re-queued {} stuck transcription(s)", stuck.size());
        }
    }
}
