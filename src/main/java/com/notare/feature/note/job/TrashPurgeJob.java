package com.notare.feature.note.job;

import com.notare.feature.note.service.NoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Deletes trashed notes for good once their purge time has passed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrashPurgeJob {

    private final NoteService noteService;

    @Value("${notare.trash.purge-batch-size:200}")
    private int batchSize = 200;

    @Scheduled(cron = "0 30 3 * * *")
    @SchedulerLock(name = "trashPurge", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void purge() {
        LocalDateTime now = LocalDateTime.now();
        long afterId = 0L;
        int purged = 0;
        int failed = 0;

        while (true) {
            List<Long> ids = noteService.findPurgeDueIds(now, afterId, batchSize);
            for (Long id : ids) {
                try {
                    if (noteService.purgeExpiredNote(id, now)) {
                        purged++;
                    }
                } catch (RuntimeException ex) {
                    failed++;
                    log.warn("Trash purge could not delete note {}: {}", id, ex.getMessage());
                }
                afterId = id;
            }
            if (ids.size() < batchSize) {
                break;
            }
        }

        log.info("Trash purge before {}: {} notes deleted, {} failed", now, purged, failed);
    }
}
