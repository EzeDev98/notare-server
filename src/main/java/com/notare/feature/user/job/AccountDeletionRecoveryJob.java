package com.notare.feature.user.job;

import com.notare.feature.user.model.AccountDeletion;
import com.notare.feature.user.repository.AccountDeletionRepository;
import com.notare.feature.user.service.AccountDeletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Finishes account deletions that stopped halfway (step 17g, B-03). Once the auth user is deleted the
 * person can no longer sign in to ask again, so nobody else would ever continue. One that still fails
 * is logged and tried again on the next run. The lock keeps a second instance out (SchedulerLockConfig).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionRecoveryJob {

    /** A deletion that has not moved for this many minutes is stuck, not running. */
    private static final long STUCK_AFTER_MINUTES = 10;

    private final AccountDeletionRepository deletions;
    private final AccountDeletionService deletionService;

    @Scheduled(fixedDelayString = "PT10M")
    @SchedulerLock(name = "accountDeletionRecovery", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void resumeStuck() {
        List<AccountDeletion> stuck = deletions.findByUpdatedAtBefore(LocalDateTime.now().minusMinutes(STUCK_AFTER_MINUTES));
        for (AccountDeletion deletion : stuck) {
            try {
                deletionService.resume(deletion.getUserId());
            } catch (RuntimeException ex) {
                log.warn("Account deletion {} is still stuck: {}", deletion.getUserId(), ex.getMessage());
            }
        }
        if (!stuck.isEmpty()) {
            log.info("Account deletion recovery: {} deletion(s) picked up", stuck.size());
        }
    }
}
