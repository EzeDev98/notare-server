package com.notare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled support.
 *
 * <p>Its own class rather than an annotation bolted onto AsyncConfig: that one
 * exists to configure the transcription executor, and scheduling is unrelated.
 * Keeping them apart means neither is deleted by accident when the other's
 * feature is reworked.
 *
 * <p>Worth knowing before this reaches production: @Scheduled runs on EVERY
 * instance. With more than one application node, the retention sweep below
 * fires once per node. It's idempotent (a delete by cutoff), so concurrent runs
 * are harmless here — but that is a property of this particular job, not of
 * scheduling in general, and the next @Scheduled job added to this application
 * needs its own answer (a lock via ShedLock, or a single designated scheduler
 * node).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}