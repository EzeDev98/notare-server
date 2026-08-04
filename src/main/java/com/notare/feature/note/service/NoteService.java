package com.notare.feature.note.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.CreateNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import org.springframework.data.domain.Pageable;

/**
 * Every method takes ownerId (a plain Long) rather than a User or UserDetail.
 * That keeps this service decoupled from Spring Security types, and — more
 * importantly — the ownerId is threaded into every repository call, so
 * ownership is enforced on every single operation, not just reads.
 */
public interface NoteService {

    NoteResponse createNote(Long ownerId, CreateNoteRequest request);

    NoteResponse getNote(Long ownerId, Long noteId);

    PagedResponse<NoteSummaryResponse> listNotes(Long ownerId, Pageable pageable);

    PagedResponse<NoteSummaryResponse> searchNotes(Long ownerId, String query, Pageable pageable);

    NoteResponse updateNote(Long ownerId, Long noteId, UpdateNoteRequest request);

    NoteResponse autoSave(Long ownerId, Long noteId, AutoSaveNoteRequest request);

    void softDeleteNote(Long ownerId, Long noteId);

    NoteResponse restoreNote(Long ownerId, Long noteId);

    void permanentlyDeleteNote(Long ownerId, Long noteId);
}