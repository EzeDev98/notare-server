package com.notare.feature.sync.service.impl;

import com.notare.feature.note.dto.request.CreateNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.sync.dto.request.SyncOperationRequest;
import com.notare.feature.sync.dto.response.SyncOperationResult;
import com.notare.feature.sync.enums.SyncResultStatus;
import com.notare.feature.sync.model.SyncOperation;
import com.notare.feature.sync.repository.SyncOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies exactly ONE queued operation, in its own transaction.
 *
 * <h2>Why this is a separate bean</h2>
 *
 * It has to be. @Transactional works through a Spring AOP proxy, and a call
 * from one method to another INSIDE the same bean bypasses that proxy entirely
 * — the annotation is silently ignored and the code runs in whatever
 * transaction the caller happened to have. This is the same self-invocation
 * trap as @Async in Step 5, and here it would be quietly catastrophic: the
 * batch would run as one transaction, so a conflict on operation seven would
 * roll back the six that already succeeded, which is precisely the failure
 * Step 9 was designed to avoid.
 *
 * <p>REQUIRES_NEW rather than REQUIRED, even though the orchestrator is
 * deliberately non-transactional and REQUIRED would behave identically today.
 * It states the requirement rather than depending on the caller continuing to
 * be careful: if anyone ever wraps the push service in a transaction, this
 * still isolates each operation instead of silently joining theirs.
 *
 * <h2>What is inside the transaction</h2>
 *
 * The ledger check, the note change, and the ledger write. All three, together,
 * on purpose. If the ledger write could commit separately from the note change,
 * the two could disagree — and a missing ledger row means the next replay
 * reports a CONFLICT against the user's own successful write, which is the
 * phantom conflict this whole mechanism exists to prevent.
 *
 * <p>Conflicts and missing notes are NOT caught here. They propagate, so the
 * transaction rolls back cleanly and the orchestrator classifies them. Catching
 * a conflict in-place would mean continuing to use a persistence context that
 * an optimistic-lock failure has already left in an unusable state.
 */
@Component
@RequiredArgsConstructor
public class SyncOperationApplier {

    private final NoteService noteService;
    private final SyncOperationRepository syncOperationRepository;

    /**
     * @throws com.notare.common.exception.NoteConflictException  stale version
     * @throws com.notare.common.exception.NoteNotFoundException  note gone
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncOperationResult apply(Long ownerId, SyncOperationRequest request) {

        // 1. Already done? Replay the recorded result instead of the operation.
        var recorded = syncOperationRepository
                .findByOwnerIdAndOperationId(ownerId, request.getOperationId());

        if (recorded.isPresent()) {
            // The note may be absent (a replayed DELETE, or a later permanent
            // delete) and that's fine — APPLIED is still the honest answer. The
            // client only needs to know it can drop this from its queue.
            NoteResponse note = noteService
                    .findAnyNote(ownerId, recorded.get().getNoteId())
                    .orElse(null);
            return applied(request, note);
        }

        // 2. Do the work.
        NoteResponse note = switch (request.getType()) {
            case CREATE -> create(ownerId, request);
            case UPDATE -> update(ownerId, request);
            case DELETE -> delete(ownerId, request);
        };

        // 3. Record it, in this same transaction.
        record(ownerId, request, note);

        return applied(request, note);
    }

    private NoteResponse create(Long ownerId, SyncOperationRequest request) {
        CreateNoteRequest createRequest = new CreateNoteRequest();
        createRequest.setTitle(request.getTitle());
        createRequest.setContent(request.getContent());
        // createWithClientId is itself idempotent on (owner, clientId), so a
        // create is protected twice over: by the ledger above and by the unique
        // constraint underneath. Belt and braces is warranted — a duplicated
        // note is data the user has to clean up by hand.
        return noteService.createWithClientId(ownerId, createRequest, request.getClientId());
    }

    private NoteResponse update(Long ownerId, SyncOperationRequest request) {
        UpdateNoteRequest updateRequest = new UpdateNoteRequest();
        updateRequest.setTitle(request.getTitle());
        updateRequest.setContent(request.getContent());
        updateRequest.setVersion(request.getVersion());
        // The ORDINARY owner-facing update: same requireMatchingVersion, same
        // 409 semantics. An offline edit is not a special kind of write, it's
        // an ordinary write that arrived late, and routing it anywhere else
        // would fork the conflict protocol.
        return noteService.updateNote(ownerId, request.getNoteId(), updateRequest);
    }

    private NoteResponse delete(Long ownerId, SyncOperationRequest request) {
        noteService.softDeleteNote(ownerId, request.getNoteId());
        // Re-read so the ledger and the response carry the post-delete version.
        return noteService.findAnyNote(ownerId, request.getNoteId()).orElse(null);
    }

    private void record(Long ownerId, SyncOperationRequest request, NoteResponse note) {
        if (note == null) {
            // Nothing to point the ledger at. Only reachable if a note vanished
            // between the write and the re-read; skipping the entry costs at
            // most one redundant replay, which is safe.
            return;
        }
        SyncOperation operation = new SyncOperation();
        operation.setOwnerId(ownerId);
        operation.setOperationId(request.getOperationId());
        operation.setNoteId(note.id());
        operation.setResultVersion(note.version());
        syncOperationRepository.save(operation);
    }

    private SyncOperationResult applied(SyncOperationRequest request, NoteResponse note) {
        return new SyncOperationResult(
                request.getOperationId(),
                SyncResultStatus.APPLIED,
                note,
                // clientId echoed only for CREATE — it's how the client maps its
                // local note onto note.id().
                request.getClientId(),
                null);
    }
}