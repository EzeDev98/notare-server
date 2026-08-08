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
import com.notare.feature.note.dto.response.TrashedNoteResponse;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    public PagedResponse<NoteSummaryResponse> listNotes(Long ownerId, boolean archived, Pageable pageable) {
        Page<Note> page = archived
                ? noteRepository.findByOwnerIdAndDeletedFalseAndArchivedTrue(ownerId, withStableOrder(pageable))
                : noteRepository.findByOwnerIdAndDeletedFalseAndArchivedFalse(ownerId, withStableOrder(pageable));
        return toSummaryPage(page);
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
    public NoteResponse setArchived(Long ownerId, Long noteId, boolean archived) {
        Note note = requireOwnActiveNote(ownerId, noteId);
        note.setArchived(archived);
        return saveOrganisationChange(note);
    }

    /**
     * Shared tail of the two toggles above.
     *
     * <p><b>These writes DO bump the version, and that is deliberate.</b> The
     * tempting alternative is a @Modifying UPDATE that sets the column without
     * touching @Version or updatedAt, on the grounds that pinning is not really
     * an edit. It would be a mistake twice over: the note would stop appearing
     * in the delta feed, so a second device would never learn it had been
     * pinned, and no client could ever detect that it holds stale organisation
     * state. Sync exists because absence is not a synchronizable fact — a
     * change deliberately made invisible is the same problem wearing a
     * different hat.
     *
     * <p>The cost is real and the client has to handle it: a user who pins a
     * note from the list while its editor is open leaves that editor holding a
     * stale version, and its next auto-save conflicts. This endpoint returns
     * the full note precisely so a client routing every note response through
     * one store adopts the new version without a refetch.
     *
     * <p>No requireMatchingVersion, so there is no application-level conflict
     * check — but Hibernate's own @Version guard still applies at flush, so a
     * genuinely simultaneous write surfaces as a 409. Rare enough to be worth
     * a retry rather than a design.
     *
     * <p>Idempotent for free: if the flag already held the requested value,
     * Hibernate's dirty checking issues no UPDATE, so @Version does not
     * increment and re-sending returns the same version. Same property
     * auto-save gets, for the same reason.
     */
    private NoteResponse saveOrganisationChange(Note note) {
        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    /**
     * Ownership-scoped, trash-excluding lookup for the organisation toggles.
     *
     * <p>Excluding trashed notes is the point: pinning something in the bin is
     * meaningless, and allowing it would let a note sit in the trash carrying
     * organisation state that reappears unexpectedly on restore. A trashed note
     * answers 404 here, which is also what a note belonging to someone else
     * answers — the two are indistinguishable on purpose.
     */
    private Note requireOwnActiveNote(Long ownerId, Long noteId) {
        return noteRepository.findByIdAndOwnerIdAndDeletedFalse(noteId, ownerId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TrashedNoteResponse> listTrashedNotes(Long ownerId, Pageable pageable) {
        Page<Note> page = noteRepository.findByOwnerIdAndDeletedTrue(ownerId, withStableOrder(pageable));

        List<TrashedNoteResponse> content = page.getContent().stream()
                .map(note -> new TrashedNoteResponse(noteMapper.toSummary(note), note.getDeletedAt()))
                .toList();

        return PagedResponse.of(content, page);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<NoteSummaryResponse> searchNotes(Long ownerId, String query, Pageable pageable) {
        // Normalize the term: null-safe and trimmed. An empty term makes the
        // LIKE '%%' match everything, so a blank search behaves like "list all".
        String term = query == null ? "" : query.strip();

        // Neutralise wildcards AFTER trimming and BEFORE the query. Searching
        // for "%" should find notes containing a percent sign, not every note
        // the user owns; "snake_case" should not also match "snakeXcase".
        // Escaping earlier would let strip() see characters that are no longer
        // what the user typed.
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
        eventPublisher.publishEvent(new NotePermanentlyDeletedEvent(noteId, ownerId));
    }

    @Override
    @Transactional
    public NoteResponse createWithClientId(Long ownerId, CreateNoteRequest request, String clientId) {
        // Idempotency check first: this operation may well have been applied on
        // an earlier attempt whose response the client never received.
        Optional<Note> existing = noteRepository.findByOwnerIdAndClientId(ownerId, clientId);
        if (existing.isPresent()) {
            return noteMapper.toResponse(existing.get());
        }

        Note note = noteMapper.toEntity(ownerId, request);
        note.setClientId(clientId);

        try {
            return noteMapper.toResponse(noteRepository.save(note));
        } catch (DataIntegrityViolationException ex) {
            // Lost a race with a concurrent replay of the same operation —
            // uq_notes_owner_client_id fired. The other attempt succeeded, so
            // return ITS note. The constraint is the real guarantee here; the
            // lookup above just avoids the exception in the common case.
            return noteRepository.findByOwnerIdAndClientId(ownerId, clientId)
                    .map(noteMapper::toResponse)
                    .orElseThrow(() -> ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NoteResponse> findAnyNote(Long ownerId, Long noteId) {
        // findByIdAndOwnerId has no deleted filter, so this sees trashed notes
        // too — which is exactly what a replayed DELETE needs to report on.
        return noteRepository.findByIdAndOwnerId(noteId, ownerId).map(noteMapper::toResponse);
    }

    /**
     * The delta query behind sync pull.
     *
     * <p><b>The sort is rebuilt here and the caller's sort is discarded.</b>
     * That is not tidiness — it is the difference between correct and subtly
     * broken. Paging over a result set sorted only by updatedAt is undefined
     * whenever timestamps tie, and they tie constantly: MySQL DATETIME has
     * second precision unless you ask for fractional seconds, so a bulk edit
     * or an import puts dozens of rows on the same instant. With no
     * tiebreaker the database may return them in any order per page, so a
     * client paging through can see one note twice and never see another at
     * all. Adding id ASC makes the order total and therefore stable.
     *
     * <p>Leaving that to the caller would mean any endpoint that forgets it
     * silently corrupts a client's cache, with nothing failing and nothing
     * logged. The query owns its own correctness.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<NoteChange> findChangesSince(Long ownerId, LocalDateTime since, Pageable pageable) {
        Pageable stable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("id")));

        // No cursor = first sync: active notes only. Tombstones would be noise
        // for a client that has nothing cached to evict.
        Page<Note> page = (since == null)
                ? noteRepository.findByOwnerIdAndDeletedFalse(ownerId, stable)
                : noteRepository.findByOwnerIdAndUpdatedAtGreaterThanEqual(ownerId, since, stable);

        return page.map(this::toChange);
    }

    /**
     * A trashed note becomes a bare tombstone: id only, no title or content.
     * Shipping the body of something the user just deleted would be wasted
     * payload on every sync, and mildly surprising if it ever surfaced in a UI.
     */
    private NoteChange toChange(Note note) {
        return note.isDeleted()
                ? new NoteChange(note.getId(), true, null, null)
                : new NoteChange(note.getId(), false, noteMapper.toResponse(note), note.getClientId());
    }

    /**
     * Full replacement of a note's editable fields (PUT semantics), version
     * gate included.
     *
     * <p>Shared by the owner's updateNote and the collaborator's
     * updateAuthorizedNote. Both writers MUST pass the same version gate or
     * Step 2's optimistic locking silently breaks now that a two-writer race is
     * possible — and the surest way to guarantee that is for there to be one
     * gate, not two identical ones.
     *
     * <p>Layer 1 is requireMatchingVersion: it catches the common case, a
     * client editing from a version the server has already moved past, and
     * returns a clean 409 carrying the current version.
     *
     * <p>Layer 2 is saveAndFlush, which forces the UPDATE now so @Version
     * increments and @LastModifiedDate is set before the response is mapped. It
     * also means a genuinely concurrent write — two requests that both passed
     * layer 1 — trips Hibernate's own optimistic-lock check here and surfaces
     * as ObjectOptimisticLockingFailureException, also a 409.
     *
     * <p>A null title or content CLEARS it. That is the difference from
     * auto-save, and the reason the two request DTOs stay separate types
     * despite currently having identical fields.
     *
     * <p>Note what is never touched here: pinned, archived, deleted. WRITE
     * covers the note's shared substance, not the owner's private organisation
     * of it.
     */
    private NoteResponse applyFullUpdate(Note note, UpdateNoteRequest request) {
        requireMatchingVersion(request.getVersion(), note);

        note.setTitle(request.getTitle());
        note.setContent(request.getContent());

        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    /**
     * Partial update (PATCH semantics), version gate included.
     *
     * <p>Shared by the owner's autoSave and the collaborator's
     * autoSaveAuthorizedNote, for the same reason as applyFullUpdate above.
     *
     * <p>A null field means "the client didn't touch this, leave it as-is" —
     * only non-null fields are applied. To CLEAR a field the client sends an
     * empty string, not null. Getting this backwards wipes a note, which is why
     * AutoSaveNoteRequest and UpdateNoteRequest are separate types even though
     * their fields match today.
     *
     * <p>Idempotent for free: if the applied values equal what is already
     * stored, Hibernate's dirty checking sees no change and issues no UPDATE,
     * so @Version does not increment and re-sending returns the same version.
     * That property is what makes a retried auto-save safe — and it is what
     * stops a flaky connection from turning one save into a version drift the
     * client can never catch up with.
     */
    private NoteResponse applyAutoSave(Note note, AutoSaveNoteRequest request) {
        requireMatchingVersion(request.getVersion(), note);

        if (request.getTitle() != null) {
            note.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            note.setContent(request.getContent());
        }

        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    /**
     * Appends id as a final tiebreaker to whatever sort the caller asked for.
     *
     * <p>The trash is sorted by deletedAt, and deletedAt ties constantly:
     * MySQL DATETIME is second-precision unless fractional seconds are asked
     * for, and deleting several notes in a row puts them all in the same
     * second. A sort with ties is UNDEFINED between pages — the database is
     * free to order tied rows differently on each query — so page 1 can show a
     * note that page 2 shows again while a third is never returned at all.
     *
     * <p>This is the same failure Step 9 designed around when it rebuilt the
     * sync sort as (updatedAt, id). The difference is that sync DISCARDS the
     * caller's sort entirely, because its paging contract depends on one exact
     * ordering. Here the caller may legitimately sort by title or updatedAt, so
     * their choice is kept and only made deterministic.
     *
     * <p>An unsorted Pageable is left alone: without an ORDER BY there is no
     * tie to break, and adding one would silently override the database's
     * natural ordering for a caller who did not ask for any.
     */
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

        // Identical to updateNote from here down — now structurally rather than
        // by discipline. This body used to be a character-for-character copy,
        // kept in step by a comment asking future editors to change both. That
        // was fine while there was one pair; adding auto-save would have made it
        // two pairs, and a rule enforced by a comment fails quietly the first
        // time someone reads only one of the four methods.
        //
        // A collaborator's write IS an ordinary note write once authorization
        // has happened. The only difference between the two paths is how the
        // caller earned the right to be here, and that difference is entirely
        // above this line.
        return applyFullUpdate(note, request);
    }

    @Override
    @Transactional
    public NoteResponse autoSaveAuthorizedNote(Long noteId, AutoSaveNoteRequest request) {
        Note note = noteRepository.findByIdAndDeletedFalse(noteId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));

        return applyAutoSave(note, request);
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