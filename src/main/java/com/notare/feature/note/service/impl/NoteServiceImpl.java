package com.notare.feature.note.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.NoteConflictException;
import com.notare.common.exception.NoteNotFoundException;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.CreateNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.note.mapper.NoteMapper;
import com.notare.feature.note.model.Note;
import com.notare.feature.note.repository.NoteRepository;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.share.event.NotePermanentlyDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        // save() on a new entity triggers the INSERT immediately (IDENTITY id
        // generation needs it), so id, version (0) and the timestamps are all
        // populated on the returned instance.
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
        Page<Note> page = noteRepository.findByOwnerIdAndDeletedFalse(ownerId, pageable);
        return toSummaryPage(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<NoteSummaryResponse> searchNotes(Long ownerId, String query, Pageable pageable) {
        // Normalize the term: null-safe and trimmed. An empty term makes the
        // LIKE '%%' match everything, so a blank search behaves like "list all".
        String term = query == null ? "" : query.strip();
        Page<Note> page = noteRepository.searchByOwner(ownerId, term, pageable);
        return toSummaryPage(page);
    }

    @Override
    @Transactional
    public NoteResponse updateNote(Long ownerId, Long noteId, UpdateNoteRequest request) {
        Note note = noteRepository.findByIdAndOwnerIdAndDeletedFalse(noteId, ownerId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));

        // Layer 1: application-level optimistic check. Detects the common case
        // where the client is editing from a version the server has already
        // moved past, and lets us return a clean 409 with the current version.
        requireMatchingVersion(request.getVersion(), note);

        // Full replacement of the editable fields (PUT-like). A null title or
        // content clears it. Partial, skip-null semantics live in auto-save.
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());

        // Layer 2: saveAndFlush forces the UPDATE now, so @Version increments and
        // @LastModifiedDate is set before we map the response. It also means a
        // truly concurrent write (two requests that both passed layer 1) triggers
        // Hibernate's own optimistic-lock check here, surfacing as
        // ObjectOptimisticLockingFailureException -> 409.
        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public NoteResponse autoSave(Long ownerId, Long noteId, AutoSaveNoteRequest request) {
        Note note = noteRepository.findByIdAndOwnerIdAndDeletedFalse(noteId, ownerId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));

        // Same version guard as the full update (shared helper below).
        requireMatchingVersion(request.getVersion(), note);

        // Partial (PATCH) semantics: a null field means "the client didn't touch
        // this, leave it as-is". Only non-null fields are applied. To CLEAR a
        // field the client sends an empty string, not null.
        if (request.getTitle() != null) {
            note.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            note.setContent(request.getContent());
        }

        // Idempotency for free: if the applied values equal what's already
        // stored, Hibernate's dirty-checking sees no change and issues no UPDATE,
        // so @Version does NOT increment. Re-sending the same auto-save is a
        // no-op that returns the same version. A genuine change bumps the
        // version, and the client must use the returned version for its next save.
        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void softDeleteNote(Long ownerId, Long noteId) {
        Note note = noteRepository.findByIdAndOwnerIdAndDeletedFalse(noteId, ownerId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));
        note.setDeleted(true);
        note.setDeletedAt(LocalDateTime.now());
        noteRepository.save(note);
    }

    @Override
    @Transactional
    public NoteResponse restoreNote(Long ownerId, Long noteId) {
        Note note = noteRepository.findByIdAndOwnerIdAndDeletedTrue(noteId, ownerId)
                .orElseThrow(() -> new NoteNotFoundException("Deleted note not found."));
        note.setDeleted(false);
        note.setDeletedAt(null);
        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void permanentlyDeleteNote(Long ownerId, Long noteId) {
        // Fetch ownership-scoped FIRST, then delete the managed entity.
        // We never call deleteById(noteId): that would delete by primary key
        // alone, bypassing the owner check and letting one user wipe another's
        // note if they guessed the id.
        Note note = noteRepository.findByIdAndOwnerId(noteId, ownerId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));
        noteRepository.delete(note);

        // Announce the deletion so anything referencing this note can clean up.
        // The share module listens; this module doesn't know that, and the
        // dependency keeps pointing share -> note with no cycle.
        //
        // Only on PERMANENT delete. Soft delete leaves grants intact, because a
        // trashed note can be restored and its collaborators should still be
        // there when it comes back — they simply can't see it meanwhile, since
        // every shared-note query already filters on deleted = false.
        eventPublisher.publishEvent(new NotePermanentlyDeletedEvent(noteId));
    }

    // ---------------------------------------------------------------------
    // Pre-authorized access — see the contract on NoteService. No ownerId, no
    // permission check: the share module has already found a grant before
    // calling any of these.
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public NoteResponse getAuthorizedNote(Long noteId) {
        Note note = noteRepository.findByIdAndDeletedFalse(noteId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));
        return noteMapper.toResponse(note);
    }

    @Override
    @Transactional
    public NoteResponse updateAuthorizedNote(Long noteId, UpdateNoteRequest request) {
        Note note = noteRepository.findByIdAndDeletedFalse(noteId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));

        // Identical to updateNote from here down, deliberately. A collaborator's
        // write is an ordinary note write once authorization has happened — same
        // version guard, same two-layer conflict detection, same saveAndFlush.
        // The ONLY difference between the two paths is how the caller earned the
        // right to be here.
        requireMatchingVersion(request.getVersion(), note);

        note.setTitle(request.getTitle());
        note.setContent(request.getContent());

        // Note what is NOT touched: pinned, archived, deleted. WRITE covers the
        // note's shared substance, not the owner's private organisation of it.
        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, NoteSummaryResponse> getAuthorizedNoteSummaries(Collection<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            // Guard the empty case explicitly: an IN () clause is invalid SQL on
            // some databases, and there's nothing to ask for anyway.
            return Map.of();
        }
        return noteRepository.findByIdInAndDeletedFalse(noteIds).stream()
                .collect(Collectors.toMap(Note::getId, noteMapper::toSummary));
    }

    /**
     * Maps a Page<Note> to our PagedResponse of summaries. Extracted so the
     * list and search paths share one place for entity->DTO + envelope building.
     */
    private PagedResponse<NoteSummaryResponse> toSummaryPage(Page<Note> page) {
        List<NoteSummaryResponse> content = page.getContent().stream()
                .map(noteMapper::toSummary)
                .toList();
        return PagedResponse.of(content, page);
    }

    /**
     * Compares the version the client based its edit on against the note's
     * current version. A mismatch means someone else saved in between, so we
     * abort with a 409 that carries the current version for reconciliation.
     * Shared by the full update, the auto-save, and the collaborator write path.
     */
    private void requireMatchingVersion(Long expectedVersion, Note note) {
        if (!note.getVersion().equals(expectedVersion)) {
            throw new NoteConflictException(
                    "This note has been modified since you loaded it.",
                    note.getVersion());
        }
    }
}