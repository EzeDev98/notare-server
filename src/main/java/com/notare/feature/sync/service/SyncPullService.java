package com.notare.feature.sync.service;

import com.notare.feature.sync.dto.response.SyncPullResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/**
 * The read half of sync: "what changed since I last looked?"
 *
 * <p>Kept separate from the push side (9D) for a concrete reason, not symmetry.
 * This service is read-only from top to bottom and would naturally carry
 * @Transactional(readOnly = true). The push side must deliberately NOT be
 * transactional at the method level, because each operation in a batch needs
 * its own transaction so one conflict can't roll back nineteen successes.
 *
 * <p>Put both in one class and sooner or later someone annotates the class with
 * @Transactional — a change that looks like a tidy-up and silently converts the
 * batch into all-or-nothing. Separate classes make that mistake impossible to
 * make by accident.
 */
public interface SyncPullService {

    /**
     * @param since  the client's cursor, or null for a first full sync
     * @param pageable page number and size; any SORT on it is ignored, since
     *                 delta paging is only correct under one specific ordering
     */
    SyncPullResponse pull(Long ownerId, LocalDateTime since, Pageable pageable);
}