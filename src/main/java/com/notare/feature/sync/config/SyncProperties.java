package com.notare.feature.sync.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tuning for delta sync. Registered with @Component on the class, matching
 * OtpProperties / StorageProperties / VoiceNoteProperties.
 *
 * <p>Both values have working defaults, so no application.properties change is
 * required. Override under notare.sync.* if you need to.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notare.sync")
public class SyncProperties {

    /**
     * How far BEFORE the client's cursor to start the delta window, in seconds.
     *
     * <p>This is the overlap that makes a timestamp cursor safe. A transaction
     * that began before a cursor was issued but commits after it would
     * otherwise fall into the gap between two windows and never be sent again —
     * silent, permanent data loss with nothing to indicate it happened.
     * Re-reading a few seconds of already-seen rows costs a little bandwidth
     * and the client's merge discards them.
     *
     * <p>The window must comfortably exceed your longest write transaction plus
     * any clock skew between application instances. 5s is generous for this
     * app; raise it if you ever see clients missing changes, and treat that as
     * a signal to build the proper change-log rather than to keep raising it.
     */
    private int overlapSeconds = 5;

    /** Default page size for a pull when the client doesn't specify one. */
    private int defaultPageSize = 100;

    /** Hard ceiling on a pull page, so one client can't ask for the whole table. */
    private int maxPageSize = 500;

    /**
     * How long sync bookkeeping (the operation ledger and note tombstones) is
     * kept, in days.
     *
     * <p>This is a correctness setting disguised as a housekeeping one, and it
     * cuts both ways. Too SHORT and a device that was offline longer than the
     * window comes back to find its ledger entries swept — so replayed updates
     * report phantom conflicts against the user's own writes — and its
     * tombstones gone, so permanently deleted notes linger in its cache
     * forever. Too LONG and both tables grow without bound.
     *
     * <p>The window must exceed the longest plausible period a device stays
     * offline and then reconnects expecting to sync rather than start fresh.
     * 30 days is a deliberately generous default for a notes app; a client that
     * has been away longer should be doing a full resync anyway.
     */
    private int retentionDays = 30;
}