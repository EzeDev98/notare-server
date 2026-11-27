package com.notare.feature.sync.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** The result of each operation in a pushed queue. */
public record SyncBatchResponse(

        List<SyncOperationResult> results,

        int appliedCount,

        LocalDateTime serverTime
) {
}
