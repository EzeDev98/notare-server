package com.notare.feature.sync.dto.response;

import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.sync.enums.SyncResultStatus;

/** The result of one pushed operation. */
public record SyncOperationResult(

        String operationId,

        SyncResultStatus status,

        NoteResponse note,

        String clientId,

        String message
) {
}
