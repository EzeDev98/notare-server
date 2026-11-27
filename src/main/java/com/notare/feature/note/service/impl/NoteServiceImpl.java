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
        // The save below takes the version up by one; marking that version as
        // tidied stops AI tidy redoing the same change straight away.
        note.setTidiedVersion(note.getVersion() + 1);

        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    /**
     * Shared tail of the pin toggle above.
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
    public void permanentlyDeleteNote(Long ownerId, Long noteId) {
        // Fetch ownership-scoped FIRST, then delete the managed entity.
        // We never call deleteById(noteId): that would delete by primary key
        // alone, bypassing the owner check and letting one user wipe another's
        // note if they guessed the id.
        Note note = noteRepository.findByIdAndOwnerId(noteId, ownerId)
                .orElseThrow(() -> new NoteNotFoundException("Note not found."));
        noteRepository.delete(note);

        // Announce the deletion so its clips, transcripts, stored files and the sync tombstone are cleaned up.
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
        // Re-read inside this transaction: the note may have been restored or removed since the job listed it.
        Note note = noteRepository.findById(noteId).orElse(null);
        if (note == null || !note.isDeleted() || note.getPurgeAt() == null || !note.getPurgeAt().isBefore(now)) {
            return false;
        }
        Long ownerId = note.getOwnerId();
        noteRepository.delete(note);
        // The same event a manual permanent delete publishes, so every listener cleans up the same way.
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
        // Idempotency check first: this operation may well have been applied on
        // an earlier attempt whose response the client never received.
        Optional<Note> existing = noteRepository.findByOwnerIdAndClientId(ownerId, clientId);
        if (existing.isPresent()) {
            return new NoteCreation(noteMapper.toResponse(existing.get()), false);
        }

        Note note = noteMapper.toEntity(ownerId, request);
        note.setClientId(clientId);

        try {
            return new NoteCreation(noteMapper.toResponse(noteRepository.save(note)), true);
        } catch (DataIntegrityViolationException ex) {
            // Lost a race with a concurrent replay of the same operation —
            // uq_notes_owner_client_id fired. The other attempt succeeded, so
            // return ITS note. The constraint is the real guarantee here; the
            // lookup above just avoids the exception in the common case.
            return noteRepository.findByOwnerIdAndClientId(ownerId, clientId)
                    .map(existingNote -> new NoteCreation(noteMapper.toResponse(existingNote), false))
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
        Sort stableSort = Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("id"));

        // Pageable.unpaged() is a legitimate "everything, just sorted" request —
        // its getPageNumber()/getPageSize()/getOffset() deliberately throw, so
        // building a PageRequest from them here would crash. Only getSort() is
        // safe to call on it, so an unpaged request stays unpaged; we just
        // replace whatever sort it carries with the stable one below.
        Pageable stable = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), stableSort)
                : Pageable.unpaged(stableSort);

        // No cursor = first sync: active notes only. Tombstones would be noise
        // for a client that has nothing cached to evict.
        Page<Note> page = (since == null)
                ? noteRepository.findByOwnerIdAndDeletedFalse(ownerId, stable)
                : noteRepository.findByOwnerIdAndUpdatedAtGreaterThanEqual(ownerId, since, stable);

        return page.map(this::toChange);
    }

    /**
     * A trashed note becomes a bare tombstone: id only, no content.
     * Shipping the body of something the user just deleted would be wasted
     * payload on every sync, and mildly surprising if it ever surfaced in a UI.
     */
    private NoteChange toChange(Note note) {
        return note.isDeleted()
                ? new NoteChange(note.getId(), true, null, null, note.getPurgeAt())
                : new NoteChange(note.getId(), false, noteMapper.toResponse(note), note.getClientId());
    }

    /**
     * Full replacement of a note's editable fields (PUT semantics).
     *
     * <p>The request's version must be the note's current one
     * (requireMatchingVersion); a stale one is refused with 409.
     *
     * <p>saveAndFlush forces the UPDATE now so @Version increments and
     * @LastModifiedDate is set before the response is mapped.
     *
     * <p>A null content CLEARS it. That is the difference from
     * auto-save, and the reason the two request DTOs stay separate types
     * despite currently having identical fields.
     *
     * <p>Note what is never touched here: pinned, deleted.
     */
    private NoteResponse applyFullUpdate(Note note, UpdateNoteRequest request) {
        requireMatchingVersion(note, request.getVersion());
        note.setContent(request.getContent());

        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    /**
     * Partial update (PATCH semantics). A stale version is refused with 409 (requireMatchingVersion).
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
        requireMatchingVersion(note, request.getVersion());
        if (request.getContent() != null) {
            note.setContent(request.getContent());
        }

        Note saved = noteRepository.saveAndFlush(note);
        return noteMapper.toResponse(saved);
    }

    /**
     * Layer 1 of the optimistic locking (step 17a, D-17): the version the client edited from must be
     * the note's current one, compared with equals. A stale one is a 409 carrying the current version.
     *
     * <p>A null version is the sync path, which has its own operation ledger; the REST endpoints
     * refuse a missing one before they get here.
     */
    private static void requireMatchingVersion(Note note, Long version) {
        if (version == null) {
            return;
        }
        if (!version.equals(note.getVersion())) {
            throw new NoteConflictException(note.getId(), note.getVersion());
        }
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
     * ordering. Here the caller may legitimately sort by updatedAt or another
     * note field, so their choice is kept and only made deterministic.
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
}