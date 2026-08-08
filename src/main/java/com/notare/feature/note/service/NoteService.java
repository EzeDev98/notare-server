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
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Every OWNER-facing method takes ownerId (a plain Long) rather than a User or
 * UserDetail. That keeps this service decoupled from Spring Security types,
 * and — more importantly — the ownerId is threaded into every repository call,
 * so ownership is enforced on every single operation, not just reads.
 *
 * <p>The three methods at the bottom are the exception, and they're labelled as
 * such. They exist for the share module (Step 7), which authorizes a caller via
 * a grant row and then needs to actually touch the note.
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

    // ---------------------------------------------------------------------
    // Pre-authorized access (share module only).
    //
    // These take NO userId and perform NO permission check. The caller must
    // have already established that the requester may read (or write) the note
    // — in practice, by finding a NoteShare grant. Calling one of these from a
    // controller, or from anywhere that hasn't checked a grant, is a privilege
    // escalation: any authenticated user could read any note by guessing an id.
    //
    // The "authorized" in each name is a claim the CALLER makes, not something
    // these methods verify.
    // ---------------------------------------------------------------------

    /** Reads an active note by id, ownership unchecked. Throws 404 if it doesn't exist or is trashed. */
    NoteResponse getAuthorizedNote(Long noteId);

    /**
     * Updates title and content of an active note by id, ownership unchecked.
     *
     * <p>Runs the same version guard as {@link #updateNote} — a stale version
     * still yields a 409 carrying the current one. That's the point of routing
     * collaborator writes through here rather than reimplementing them: the
     * owner and a collaborator editing at once is precisely the race Step 2's
     * optimistic locking was built for, and both writers must go through the
     * same gate for it to work.
     */
    NoteResponse updateAuthorizedNote(Long noteId, UpdateNoteRequest request);

    /**
     * Resolves a batch of note ids to summaries in one query, ownership
     * unchecked. Keyed by note id, so callers can look up each row's note
     * without depending on result order.
     *
     * <p>Ids that are unknown or soft-deleted are simply absent from the map —
     * the caller decides whether that's a skipped row or an error.
     */
    Map<Long, NoteSummaryResponse> getAuthorizedNoteSummaries(Collection<Long> noteIds);
}