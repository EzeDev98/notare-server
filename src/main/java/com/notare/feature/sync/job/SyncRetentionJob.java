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

/**
 * Prunes sync bookkeeping that has outlived its usefulness.
 *
 * <p>Both tables grow monotonically with use: a row per successful offline
 * operation, and a row per permanently deleted note, forever. Neither is ever
 * updated and neither is read after its window has passed, so without a sweep
 * they are pure accumulation — the kind of table that is fine for a year and
 * then is not.
 *
 * <p><b>The cutoff is a correctness decision, not a disk-space one.</b> Deleting
 * a ledger entry too early means a returning client's replayed update reports a
 * phantom conflict against its own successful write. Deleting a tombstone too
 * early means a permanently deleted note lives on in that device's cache
 * forever. Both failures are silent and both look like bugs elsewhere, so the
 * window has to comfortably exceed how long a device might plausibly be away.
 * See SyncProperties.retentionDays.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncRetentionJob {

    private final SyncOperationRepository syncOperationRepository;
    private final NoteTombstoneRepository noteTombstoneRepository;
    private final SyncProperties syncProperties;

    /**
     * Runs nightly at 03:15. A fixed time rather than fixedDelay so it lands in
     * a quiet window instead of drifting into peak hours after each restart.
     *
     * <p>@Transactional because both bulk deletes need one, and putting them in
     * the SAME transaction is deliberate: the two tables describe the same
     * retention window, and letting one succeed while the other fails would
     * leave them describing different ones.
     */
    @Scheduled(cron = "0 15 3 * * *")
    @SchedulerLock(name = "syncRetention", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void sweep() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(syncProperties.getRetentionDays());

        int operations = syncOperationRepository.deleteOlderThan(cutoff);
        int tombstones = noteTombstoneRepository.deleteOlderThan(cutoff);

        // Logged unconditionally, including zeroes. A sweep that silently stops
        // running looks exactly like a sweep with nothing to do, and the first
        // symptom of the difference is a table nobody is watching.
        log.info("Sync retention sweep before {}: {} operations, {} tombstones removed",
                cutoff, operations, tombstones);
    }
}