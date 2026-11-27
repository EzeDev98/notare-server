package com.notare.feature.note.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.CreateNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteChange;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Creates, reads, updates and deletes a user's notes. */
public interface NoteService {

    NoteResponse createNote(Long ownerId, CreateNoteRequest request);

    NoteResponse getNote(Long ownerId, Long noteId);

    PagedResponse<NoteSummaryResponse> listNotes(Long ownerId, Pageable pageable);

    NoteResponse setPinned(Long ownerId, Long noteId, boolean pinned);

    NoteResponse restoreOriginal(Long ownerId, Long noteId);

    PagedResponse<NoteSummaryResponse> searchNotes(Long ownerId, String query, Pageable pageable);

    NoteResponse updateNote(Long ownerId, Long noteId, UpdateNoteRequest request);

    NoteResponse autoSave(Long ownerId, Long noteId, AutoSaveNoteRequest request);

    void permanentlyDeleteNote(Long ownerId, Long noteId);

    NoteResponse createWithClientId(Long ownerId, CreateNoteRequest request, String clientId);

    NoteCreation createIdempotent(Long ownerId, CreateNoteRequest request, String clientId);

    Optional<NoteResponse> findAnyNote(Long ownerId, Long noteId);

    Page<NoteChange> findChangesSince(Long ownerId, LocalDateTime since, Pageable pageable);

    List<Long> findPurgeDueIds(LocalDateTime now, Long afterId, int limit);

    boolean purgeExpiredNote(Long noteId, LocalDateTime now);
}
