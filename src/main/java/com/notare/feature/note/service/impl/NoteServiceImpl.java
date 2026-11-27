package com.notare.feature.note.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.NoteConflictException;
import com.notare.common.exception.NoteNotFoundException;
import com.notare.common.util.LikeEscaper;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.CreateNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteChange;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.note.service.NoteCreation;
import com.notare.feature.note.event.NotePermanentlyDeletedEvent;
import com.notare.feature.note.mapper.NoteMapper;
import com.notare.feature.note.model.Note;
import com.notare.feature.note.repository.NoteRepository;
import com.notare.feature.note.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Creates, reads, updates and deletes a user's notes. */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteRepository noteRepository;
    private final NoteMapper noteMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public NoteResponse createNote(Long ownerId, CreateNoteRequest request) {
        Note note = noteMapper.toEntity(ownerId, request);
        Note saved = noteRepository.save(note);
        return noteMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NoteResponse getNote(Long ownerId, Long noteId) {
        Note note = noteRepository.findByIdAndOwnerIdAndDeletedFalse(noteId, ownerId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));
        return noteMapper.toResponse(note);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<NoteSummaryResponse> listNotes(Long ownerId, Pageable pageable) {
        return toSummaryPage(noteRepository.findByOwnerIdAndDeletedFalse(ownerId, withStableOrder(pageable)));
    }

    @Override
    @Transactional
    public NoteResponse setPinned(Long ownerId, Long noteId, boolean pinned) {
        Note note = requireOwnActiveNote(ownerId, noteId);
        note.setPinned(pinned);
        return saveOrganisationChange(note);
    }

    @Override
    @Transactional
    public NoteResponse restoreOriginal(Long ownerId, Long noteId) {
        Note note = requireOwnActiveNote(ownerId, noteId);
        if (!noteMapper.isRestorable(note)) {
            throw new NoteConflictException(noteId, note.getVersion());
        }

        note.setContent(note.getPreTidyContent());
        note.setPreTidyContent(null);
        note.setTidiedVersion(note.getVersion() + 1);

        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    private NoteResponse saveOrganisationChange(Note note) {
        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    private Note requireOwnActiveNote(Long ownerId, Long noteId) {
        return noteRepository.findByIdAndOwnerIdAndDeletedFalse(noteId, ownerId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<NoteSummaryResponse> searchNotes(Long ownerId, String query, Pageable pageable) {
        String term = query == null ? "" : query.strip();

        Page<Note> page = noteRepository.searchByOwner(ownerId, LikeEscaper.escape(term), pageable);
        return toSummaryPage(page);
    }

    @Override
    @Transactional
    public NoteResponse updateNote(Long ownerId, Long noteId, UpdateNoteRequest request) {
        Note note = noteRepository.findByIdAndOwnerIdAndDeletedFalse(noteId, ownerId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));

        return applyFullUpdate(note, request);
    }

    @Override
    @Transactional
    public NoteResponse autoSave(Long ownerId, Long noteId, AutoSaveNoteRequest request) {
        Note note = noteRepository.findByIdAndOwnerIdAndDeletedFalse(noteId, ownerId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));

        return applyAutoSave(note, request);
    }

    @Override
    @Transactional
    public void permanentlyDeleteNote(Long ownerId, Long noteId) {
        Note note = noteRepository.findByIdAndOwnerId(noteId, ownerId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));
        noteRepository.delete(note);

        eventPublisher.publishEvent(new NotePermanentlyDeletedEvent(noteId, ownerId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findPurgeDueIds(LocalDateTime now, Long afterId, int limit) {
        return noteRepository.findPurgeDueIds(now, afterId, PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public boolean purgeExpiredNote(Long noteId, LocalDateTime now) {
        Note note = noteRepository.findById(noteId).orElse(null);
        if (note == null || !note.isDeleted() || note.getPurgeAt() == null || !note.getPurgeAt().isBefore(now)) {
            return false;
        }
        Long ownerId = note.getOwnerId();
        noteRepository.delete(note);
        eventPublisher.publishEvent(new NotePermanentlyDeletedEvent(noteId, ownerId));
        return true;
    }

    @Override
    @Transactional
    public NoteResponse createWithClientId(Long ownerId, CreateNoteRequest request, String clientId) {
        return createIdempotent(ownerId, request, clientId).note();
    }

    @Override
    @Transactional
    public NoteCreation createIdempotent(Long ownerId, CreateNoteRequest request, String clientId) {
        Optional<Note> existing = noteRepository.findByOwnerIdAndClientId(ownerId, clientId);
        if (existing.isPresent()) {
            return new NoteCreation(noteMapper.toResponse(existing.get()), false);
        }

        Note note = noteMapper.toEntity(ownerId, request);
        note.setClientId(clientId);

        try {
            return new NoteCreation(noteMapper.toResponse(noteRepository.save(note)), true);
        } catch (DataIntegrityViolationException ex) {
            return noteRepository.findByOwnerIdAndClientId(ownerId, clientId)
                    .map(existingNote -> new NoteCreation(noteMapper.toResponse(existingNote), false))
                    .orElseThrow(() -> ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NoteResponse> findAnyNote(Long ownerId, Long noteId) {
        return noteRepository.findByIdAndOwnerId(noteId, ownerId).map(noteMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NoteChange> findChangesSince(Long ownerId, LocalDateTime since, Pageable pageable) {
        Sort stableSort = Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("id"));

        Pageable stable = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), stableSort)
                : Pageable.unpaged(stableSort);

        Page<Note> page = (since == null)
                ? noteRepository.findByOwnerIdAndDeletedFalse(ownerId, stable)
                : noteRepository.findByOwnerIdAndUpdatedAtGreaterThanEqual(ownerId, since, stable);

        return page.map(this::toChange);
    }

    private NoteChange toChange(Note note) {
        return note.isDeleted()
                ? new NoteChange(note.getId(), true, null, null, note.getPurgeAt())
                : new NoteChange(note.getId(), false, noteMapper.toResponse(note), note.getClientId());
    }

    private NoteResponse applyFullUpdate(Note note, UpdateNoteRequest request) {
        requireMatchingVersion(note, request.getVersion());
        note.setContent(request.getContent());

        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    private NoteResponse applyAutoSave(Note note, AutoSaveNoteRequest request) {
        requireMatchingVersion(note, request.getVersion());
        if (request.getContent() != null) {
            note.setContent(request.getContent());
        }

        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    private static void requireMatchingVersion(Note note, Long version) {
        if (version == null) {
            return;
        }
        if (!version.equals(note.getVersion())) {
            throw new NoteConflictException(note.getId(), note.getVersion());
        }
    }

    private Pageable withStableOrder(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.isUnsorted() || sort.getOrderFor("id") != null) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort.and(Sort.by(Sort.Order.desc("id"))));
    }

    private PagedResponse<NoteSummaryResponse> toSummaryPage(Page<Note> page) {
        List<NoteSummaryResponse> content = page.getContent().stream()
                .map(noteMapper::toSummary)
                .toList();
        return PagedResponse.of(content, page);
    }
}
