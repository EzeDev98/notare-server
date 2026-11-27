package com.notare.feature.sync.service;

import com.notare.feature.sync.dto.response.SyncPullResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/** Finds what changed for a user since their last sync. */
public interface SyncPullService {

    SyncPullResponse pull(Long ownerId, LocalDateTime since, Pageable pageable);
}
