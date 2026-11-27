package com.notare.feature.notetidy.job;

import com.notare.feature.note.repository.NoteRepository;
import com.notare.feature.notetidy.config.NoteTidyProperties;
import com.notare.feature.notetidy.service.NoteTidyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks notes for AI tidy once a minute and hands each one to the tidy pool.
 * A note still being worked on is never handed over twice. Only exists when
 * notare.tidy.enabled=true.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notare.tidy", name = "enabled", havingValue = "true")
public class NoteTidyJob {

    private final NoteRepository noteRepository;
    private final NoteTidyService noteTidyService;
    private final NoteTidyProperties properties;

    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void pick() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime idleBefore = now.minusMinutes(properties.getIdleMinutes());

        List<Long> candidates = noteRepository.findTidyCandidateIds(
                idleBefore, now, properties.getMaxAttempts(), PageRequest.of(0, properties.getBatchSize()));

        int handedOver = 0;
        for (Long noteId : candidates) {
            if (!inFlight.add(noteId)) {
                continue;
            }
            try {
                noteTidyService.tidy(noteId).whenComplete((done, failure) -> inFlight.remove(noteId));
                handedOver++;
            } catch (TaskRejectedException busy) {
                inFlight.remove(noteId);
                break; // pool is full; the rest are picked on a later run
            }
        }
        if (handedOver > 0) {
            log.debug("AI tidy: handed over {} note(s)", handedOver);
        }
    }
}
