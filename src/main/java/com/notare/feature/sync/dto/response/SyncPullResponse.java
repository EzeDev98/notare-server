package com.notare.feature.sync.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** Everything that changed for a user since their last sync. */
public record SyncPullResponse(

        List<SyncNoteResponse> changed,

        List<Long> deletedIds,

        LocalDateTime nextCursor,

        boolean hasMore,

        List<SyncClipResponse> clips
) {

    public SyncPullResponse(List<SyncNoteResponse> changed, List<Long> deletedIds,
                            LocalDateTime nextCursor, boolean hasMore) {
        this(changed, deletedIds, nextCursor, hasMore, List.of());
    }
}
