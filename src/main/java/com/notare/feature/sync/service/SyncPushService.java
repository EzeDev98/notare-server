package com.notare.feature.sync.service;

import com.notare.feature.sync.dto.request.SyncBatchRequest;
import com.notare.feature.sync.dto.response.SyncBatchResponse;

/**
 * The write half of sync: replaying a client's queue of offline operations.
 *
 * <p>The implementation is deliberately NOT transactional. See
 * SyncOperationApplier — each operation gets its own transaction so that one
 * stale edit can't roll back the rest of a user's offline work.
 */
public interface SyncPushService {

    SyncBatchResponse push(Long ownerId, SyncBatchRequest request);
}