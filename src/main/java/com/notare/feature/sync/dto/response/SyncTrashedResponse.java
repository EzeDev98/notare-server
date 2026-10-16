package com.notare.feature.sync.dto.response;

import java.time.LocalDateTime;

/**
 * A note that is in Recently deleted, and when it will be deleted for good (step 17e).
 * deletedIds still lists the same notes; this adds the date the client counts days left from.
 */
public record SyncTrashedResponse(Long noteId, LocalDateTime purgeAt) {
}
