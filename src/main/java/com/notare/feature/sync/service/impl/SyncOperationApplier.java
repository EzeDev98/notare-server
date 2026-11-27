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
import com.notare.feature.sync.enums.SyncOperationType;
import com.notare.feature.voicenote.service.VoiceNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Applies one queued operation in its own transaction. */
@Component
@RequiredArgsConstructor
public class SyncOperationApplier {

    private final NoteService noteService;
    private final SyncOperationRepository syncOperationRepository;
    private final VoiceNoteService voiceNoteService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncOperationResult apply(Long ownerId, SyncOperationRequest request) {

        if (request.getType() == SyncOperationType.CLIP_DELETE) {
            voiceNoteService.removeClip(ownerId, request.getClipId());
            return applied(request, null);
        }

        var recorded = syncOperationRepository
                .findByOwnerIdAndOperationId(ownerId, request.getOperationId());

        if (recorded.isPresent()) {
            NoteResponse note = noteService
                    .findAnyNote(ownerId, recorded.get().getNoteId())
                    .orElse(null);
            return applied(request, note);
        }

        NoteResponse note = switch (request.getType()) {
            case CREATE -> create(ownerId, request);
            case UPDATE -> update(ownerId, request);
            case DELETE -> delete(ownerId, request);
            case CLIP_DELETE -> throw new IllegalStateException("CLIP_DELETE is handled before the ledger.");
        };

        record(ownerId, request, note);

        return applied(request, note);
    }

    private NoteResponse create(Long ownerId, SyncOperationRequest request) {
        CreateNoteRequest createRequest = new CreateNoteRequest();
        createRequest.setContent(request.getContent());
        return noteService.createWithClientId(ownerId, createRequest, request.getClientId());
    }

    private NoteResponse update(Long ownerId, SyncOperationRequest request) {
        UpdateNoteRequest updateRequest = new UpdateNoteRequest();
        updateRequest.setContent(request.getContent());
        return noteService.updateNote(ownerId, request.getNoteId(), updateRequest);
    }

    private NoteResponse delete(Long ownerId, SyncOperationRequest request) {
        noteService.permanentlyDeleteNote(ownerId, request.getNoteId());
        return null;
    }

    private void record(Long ownerId, SyncOperationRequest request, NoteResponse note) {
        if (note == null) {
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
                request.getClientId(),
                null);
    }
}
