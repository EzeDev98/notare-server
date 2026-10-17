package com.notare.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Step 19b: finds stored files that no voice clip row points at, so abandoned objects do not pile up.
 * A file is an orphan when no voice_notes row (hidden or not) has its key AND it is older than the grace
 * period (an upload writes the file just before its row). By default it only LOGS orphans; set
 * notare.storage.orphan-sweep.delete=true to remove them, at most max-delete per run.
 * Only backends that can list their files take part (R2); others are skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanFileSweepJob {

    private static final int PAGE = 500;

    private final FileStorageService storage;
    private final JdbcTemplate jdbc;

    @Value("${notare.storage.orphan-sweep.enabled:true}")
    private boolean enabled;

    @Value("${notare.storage.orphan-sweep.delete:false}")
    private boolean delete;

    @Value("${notare.storage.orphan-sweep.grace-hours:24}")
    private long graceHours;

    @Value("${notare.storage.orphan-sweep.max-delete:500}")
    private int maxDelete;

    @Scheduled(cron = "${notare.storage.orphan-sweep.cron:0 30 3 * * *}")
    @SchedulerLock(name = "orphanFileSweep", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void sweep() {
        if (!enabled || !storage.supportsListing()) {
            log.info("Orphan file sweep skipped (disabled, or this storage backend cannot list its files).");
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofHours(graceHours));
        String after = null;
        int scanned = 0;
        int orphans = 0;
        int deleted = 0;

        while (true) {
            List<StoredObject> page = storage.list(after, PAGE);
            if (page.isEmpty()) {
                break;
            }
            after = page.get(page.size() - 1).key();
            scanned += page.size();

            String marks = String.join(",", Collections.nCopies(page.size(), "?"));
            Set<String> known = new HashSet<>(jdbc.queryForList(
                    "select storage_key from voice_notes where storage_key in (" + marks + ")",
                    String.class, page.stream().map(StoredObject::key).toArray()));

            for (StoredObject object : page) {
                if (known.contains(object.key()) || object.lastModified().isAfter(cutoff)) {
                    continue;
                }
                orphans++;
                if (delete && deleted < maxDelete) {
                    try {
                        storage.delete(object.key());
                        deleted++;
                    } catch (RuntimeException e) {
                        log.warn("Could not delete orphan stored file {}: {}", object.key(), e.getMessage());
                    }
                } else {
                    log.warn("Orphan stored file (no voice clip row): {}", object.key());
                }
            }
        }
        log.warn("Orphan file sweep finished: {} scanned, {} orphan(s), {} deleted (delete={}).",
                scanned, orphans, deleted, delete);
    }
}
