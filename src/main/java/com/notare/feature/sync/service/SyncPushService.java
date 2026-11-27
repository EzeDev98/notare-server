package com.notare.feature.sync.service;

import com.notare.feature.sync.dto.request.SyncBatchRequest;
import com.notare.feature.sync.dto.response.SyncBatchResponse;

/** Applies a client's queue of offline operations. */
public interface SyncPushService {

    SyncBatchResponse push(Long ownerId, SyncBatchRequest request);
}
