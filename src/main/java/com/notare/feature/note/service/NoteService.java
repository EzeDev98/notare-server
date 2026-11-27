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

/**
 * Every OWNER-facing method takes ownerId (a plain Long) rather than a User or
 * UserDetail. That keeps this service decoupled from Spring Security types,
 * and — more importantly — the ownerId is threaded into every repository call,
 * so ownership is enforced on every single operation, not just reads.
 */
public interface NoteService {

    NoteResponse createNote(Long ownerId, CreateNoteRequest request);

    NoteResponse getNote(Long ownerId, Long noteId);

    /** The owner's active notes. */
    PagedResponse<NoteSummaryResponse> listNotes(Long ownerId, Pageable pageable);

    /**
     * Pin or unpin a note. Idempotent: setting the state it already has is a
     * no-op that returns the note unchanged.
     *
     * <p>Takes no version and performs no conflict check, unlike every content
     * write. A version gate exists to stop one person's edit silently
     * overwriting another's prose; two devices both pinning the same note are
     * not in conflict, they agree. Requiring a version here would make pinning
     * from a list loaded thirty seconds ago fail for no benefit to anyone.
     */
    NoteResponse setPinned(Long ownerId, Long noteId, boolean pinned);

    /**
     * "Restore original": puts back the text from before AI tidy, as a normal
     * edit by the owner. Only while the note is exactly as AI tidy left it;
     * otherwise 409, because restoring would undo edits made since.
     */
    NoteResponse restoreOriginal(Long ownerId, Long noteId);

    PagedResponse<NoteSummaryResponse> searchNotes(Long ownerId, String query, Pageable pageable);

    NoteResponse updateNote(Long ownerId, Long noteId, UpdateNoteRequest request);

    NoteResponse autoSave(Long ownerId, Long noteId, AutoSaveNoteRequest request);

    /** Deletes the note for good and publishes NotePermanentlyDeletedEvent so its clips and files go too. */
    void permanentlyDeleteNote(Long ownerId, Long noteId);

    /**
     * Creates a note that a client already created offline, carrying the id it
     * gave the note locally.
     *
     * <p>IDEMPOTENT by (owner, clientId): if that pair already has a note, the
     * existing one is returned untouched rather than a second note being made.
     * That is the whole reason clientId exists — a replayed offline queue must
     * not multiply the user's notes.
     *
     * <p>Separate from {@link #createNote} rather than an extra parameter on
     * it, because the two have different contracts: createNote always creates,
     * this one may not. Hiding "sometimes this is a no-op" behind an optional
     * argument on the ordinary create is the kind of surprise that gets found
     * in production.
     */
    NoteResponse createWithClientId(Long ownerId, CreateNoteRequest request, String clientId);

    /**
     * The same idempotent create as {@link #createWithClientId}, also saying whether this call made
     * the note. POST /notes uses it to answer 201 for a new note and 200 for a repeat (step 17a).
     */
    NoteCreation createIdempotent(Long ownerId, CreateNoteRequest request, String clientId);

    /**
     * Finds one of the owner's notes whether it is active OR trashed, without
     * throwing when it's absent.
     *
     * <p>Both halves matter to sync. Trashed rows are included because a
     * replayed DELETE needs to report on a note that is, correctly, in the
     * trash. Optional rather than an exception because in the batch path
     * "missing" is an ordinary per-operation outcome, not an error — and
     * building a result object out of a caught exception is worse than asking a
     * question that can answer "no".
     */
    Optional<NoteResponse> findAnyNote(Long ownerId, Long noteId);

    /**
     * Everything of this owner's that changed at or after {@code since},
     * INCLUDING notes that were trashed — the caller needs tombstones, not just
     * live rows.
     *
     * <p>Owner-scoped like every other method here, so this is NOT one of the
     * pre-authorized accessors below; it's ordinary, safe API. The sync module
     * calls it rather than touching NoteRepository, keeping cross-module access
     * on the service boundary.
     *
     * <p>{@code since == null} means "first sync": active notes only, no
     * tombstones. A client with no cursor has no cache to evict, so sending it
     * a list of things to delete would be noise.
     *
     * <p>The sort is imposed by the implementation and any sort on the incoming
     * Pageable is ignored — see the impl for why that isn't merely tidiness.
     */
    Page<NoteChange> findChangesSince(Long ownerId, LocalDateTime since, Pageable pageable);

    /**
     * Ids of trashed notes whose purge time is before {@code now}, with id greater than
     * {@code afterId}, oldest id first, at most {@code limit} (step 17e). The job walks these
     * pages by id so one note that keeps failing cannot hold up the others.
     */
    List<Long> findPurgeDueIds(LocalDateTime now, Long afterId, int limit);

    /**
     * Deletes one expired trashed note for good and publishes NotePermanentlyDeletedEvent, so
     * clips, transcripts, stored files and the sync tombstone are cleaned up (step 17e).
     * Each call is its own transaction. Returns false when the note is gone, restored, or not due.
     */
    boolean purgeExpiredNote(Long noteId, LocalDateTime now);
}
