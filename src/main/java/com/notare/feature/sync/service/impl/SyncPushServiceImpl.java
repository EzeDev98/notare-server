package com.notare.feature.sync.service.impl;

import com.notare.common.exception.NoteConflictException;
import com.notare.common.exception.NoteNotFoundException;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.service.NoteService;
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

/**
 * Walks the queue and classifies each outcome.
 *
 * <p><b>No @Transactional anywhere in this class, and that is load-bearing.</b>
 * Every operation is applied inside SyncOperationApplier's own transaction. If
 * this method were transactional, those would join it under REQUIRED and the
 * batch would become all-or-nothing again — one conflict discarding a whole
 * day of offline work. (REQUIRES_NEW on the applier defends against exactly
 * that, but the two protections are cheap and the failure is expensive.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncPushServiceImpl implements SyncPushService {

    private final SyncOperationApplier applier;
    private final NoteService noteService;

    @Override
    public SyncBatchResponse push(Long ownerId, SyncBatchRequest request) {
        List<SyncOperationResult> results = new ArrayList<>();
        int applied = 0;
        int conflicts = 0;

        // Sequential, in the order the client queued them. NOT parallelised:
        // a client that created a note offline and then edited it sends CREATE
        // before UPDATE, and running those concurrently would try to update a
        // note that doesn't exist yet.
        for (SyncOperationRequest operation : request.getOperations()) {

            // Shape checks first, before any database work. These are the
            // cross-field rules bean validation can't express on a flat DTO
            // (see SyncOperationRequest), and a malformed operation is a client
            // bug that no retry will fix.
            String problem = validate(operation);
            if (problem != null) {
                results.add(result(operation, SyncResultStatus.INVALID, null, problem));
                continue;
            }

            try {
                SyncOperationResult result = applier.apply(ownerId, operation);
                results.add(result);
                applied++;

            } catch (NoteConflictException | OptimisticLockingFailureException ex) {
                // Somebody else wrote first. Fetch the server's CURRENT note and
                // ship it back, so the client can diff or merge immediately
                // instead of making another request to find out what it lost to.
                //
                // This read runs OUTSIDE the applier's rolled-back transaction,
                // which is why the conflict wasn't caught in there: an
                // optimistic-lock failure leaves the persistence context
                // unusable, so nothing more can safely be done inside it.
                NoteResponse current = noteService
                        .findAnyNote(ownerId, operation.getNoteId())
                        .orElse(null);
                results.add(result(operation, SyncResultStatus.CONFLICT, current,
                        "This note was modified elsewhere since your change."));
                conflicts++;

            } catch (NoteNotFoundException ex) {
                // Trashed or deleted from another device while this one was
                // offline. Not an error the user needs to see — the operation is
                // simply moot and the client drops it.
                results.add(result(operation, SyncResultStatus.NOT_FOUND, null,
                        "This note no longer exists."));
            }

            // Note what is NOT caught: anything unexpected. It propagates and
            // fails the whole request with a 500.
            //
            // That looks harsh — one bad operation ends the batch — but the
            // alternative is worse. There is no result status meaning "retry
            // later", so classifying a transient database error as INVALID
            // would tell the client to give up and DISCARD the user's edit.
            // Losing data silently is worse than failing loudly, and the ledger
            // makes the retry safe: everything already applied replays as
            // APPLIED, so the client simply sends the batch again.
        }

        // The SERVER's clock, offered as the client's next pull cursor. Taken
        // after the loop so it covers everything just written.
        return new SyncBatchResponse(results, applied, conflicts, LocalDateTime.now());
    }

    /**
     * The type-dependent rules that a single flat request DTO can't express
     * declaratively. Returns null when the operation is well formed, or a
     * message naming the missing field.
     *
     * <p>These produce INVALID rather than a 400 for the whole request, on
     * purpose: one malformed operation must not reject the nineteen valid ones
     * queued beside it.
     */
    private String validate(SyncOperationRequest operation) {
        return switch (operation.getType()) {
            case CREATE -> operation.getClientId() == null || operation.getClientId().isBlank()
                    ? "clientId is required for a CREATE operation."
                    : null;
            case UPDATE -> {
                if (operation.getNoteId() == null) {
                    yield "noteId is required for an UPDATE operation.";
                }
                // Without a version there is nothing to compare, so the write
                // would silently overwrite whatever is on the server — exactly
                // the last-write-wins behaviour this design rejected.
                yield operation.getVersion() == null
                        ? "version is required for an UPDATE operation."
                        : null;
            }
            case DELETE -> operation.getNoteId() == null
                    ? "noteId is required for a DELETE operation."
                    : null;
        };
    }

    private SyncOperationResult result(SyncOperationRequest operation,
                                       SyncResultStatus status,
                                       NoteResponse note,
                                       String message) {
        return new SyncOperationResult(
                operation.getOperationId(), status, note, operation.getClientId(), message);
    }
}