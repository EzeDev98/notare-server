package com.notare.feature.sync.job;

import com.notare.feature.sync.config.SyncProperties;
import com.notare.feature.sync.repository.NoteTombstoneRepository;
import com.notare.feature.sync.repository.SyncOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Deletes old sync records that are no longer needed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncRetentionJob {

    private final SyncOperationRepository syncOperationRepository;
    private final NoteTombstoneRepository noteTombstoneRepository;
    private final SyncProperties syncProperties;

    @Scheduled(cron = "0 15 3 * * *")
    @SchedulerLock(name = "syncRetention", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void sweep() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(syncProperties.getRetentionDays());

        int operations = syncOperationRepository.deleteOlderThan(cutoff);
        int tombstones = noteTombstoneRepository.deleteOlderThan(cutoff);

        log.info("Sync retention sweep before {}: {} operations, {} tombstones removed",
                cutoff, operations, tombstones);
    }
}
