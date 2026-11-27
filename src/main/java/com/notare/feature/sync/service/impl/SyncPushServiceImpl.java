package com.notare.feature.sync.service.impl;

import com.notare.common.exception.NoteNotFoundException;
import com.notare.common.exception.VoiceNoteNotFoundException;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.sync.dto.request.SyncBatchRequest;
import com.notare.feature.sync.dto.request.SyncOperationRequest;
import com.notare.feature.sync.dto.response.SyncBatchResponse;
import com.notare.feature.sync.dto.response.SyncOperationResult;
import com.notare.feature.sync.enums.SyncResultStatus;
import com.notare.feature.sync.service.SyncPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Applies a client's queue of offline operations and reports each result. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncPushServiceImpl implements SyncPushService {

    private final SyncOperationApplier applier;

    @Override
    public SyncBatchResponse push(Long ownerId, SyncBatchRequest request) {
        List<SyncOperationResult> results = new ArrayList<>();
        int applied = 0;

        for (SyncOperationRequest operation : request.getOperations()) {

            String problem = validate(operation);
            if (problem != null) {
                results.add(result(operation, SyncResultStatus.INVALID, null, problem));
                continue;
            }

            try {
                SyncOperationResult result = applyWithRetry(ownerId, operation);
                results.add(result);
                applied++;

            } catch (NoteNotFoundException ex) {
                results.add(result(operation, SyncResultStatus.NOT_FOUND, null,
                        "This note no longer exists."));
            } catch (VoiceNoteNotFoundException ex) {
                results.add(result(operation, SyncResultStatus.NOT_FOUND, null,
                        "This clip no longer exists."));
            }

        }

        return new SyncBatchResponse(results, applied, LocalDateTime.now());
    }

    private String validate(SyncOperationRequest operation) {
        return switch (operation.getType()) {
            case CREATE -> operation.getClientId() == null || operation.getClientId().isBlank()
                    ? "clientId is required for a CREATE operation."
                    : null;
            case UPDATE -> operation.getNoteId() == null
                    ? "noteId is required for an UPDATE operation."
                    : null;
            case DELETE -> operation.getNoteId() == null
                    ? "noteId is required for a DELETE operation."
                    : null;
            case CLIP_DELETE -> operation.getClipId() == null
                    ? "clipId is required for a CLIP_DELETE operation."
                    : null;
        };
    }

    private SyncOperationResult applyWithRetry(Long ownerId, SyncOperationRequest operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return applier.apply(ownerId, operation);
            } catch (OptimisticLockingFailureException ex) {
                if (attempt >= 3) {
                    throw ex;
                }
            }
        }
    }

    private SyncOperationResult result(SyncOperationRequest operation,
                                       SyncResultStatus status,
                                       NoteResponse note,
                                       String message) {
        return new SyncOperationResult(
                operation.getOperationId(), status, note, operation.getClientId(), message);
    }
}
