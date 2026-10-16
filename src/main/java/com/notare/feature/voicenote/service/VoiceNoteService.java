package com.notare.feature.voicenote.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.ClipChange;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.dto.response.VoiceUpload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Like NoteService, every method takes ownerId and threads it into ownership-
 * scoped repository calls. The upload method accepts a MultipartFile: a small,
 * deliberate web coupling, because upload orchestration (validate -> store ->
 * persist) belongs in the service, not the controller.
 */
public interface VoiceNoteService {

    VoiceNoteResponse upload(Long ownerId, MultipartFile file, Long noteId, Integer durationSeconds);

    /**
     * Stores a clip as a child of a note, idempotent on {@code clientId} (step 17b, B-09). The same
     * owner and client id again returns the clip already stored, before any bytes are written.
     * Access to the note (owner or Editor) is checked by the caller: this module cannot see shares.
     */
    VoiceUpload uploadForNote(Long uploaderId, Long noteId, MultipartFile file, String clientId, Integer durationSeconds);

    VoiceNoteResponse getVoiceNote(Long ownerId, Long voiceNoteId);

    PagedResponse<VoiceNoteResponse> listVoiceNotes(Long ownerId, Pageable pageable);

    List<VoiceNoteResponse> listVoiceNotesForNote(Long ownerId, Long noteId);

    /**
     * Loads the audio bytes plus the metadata needed to serve them. Backs
     * both the download and stream endpoints.
     *
     * <p>Authorized by direct ownership OR by owning the note this
     * recording is attached to — see the implementation's own doc comment
     * for why "ownership-scoped" alone stopped being accurate once a
     * recording's uploader and its parent note's owner can differ.
     */
    VoiceNoteContent loadAudio(Long ownerId, Long voiceNoteId);

    void deleteVoiceNote(Long ownerId, Long voiceNoteId);

    /**
     * The clips of this owner that changed since the cursor, for the sync pull (step 17c). A null
     * cursor is a first sync: visible clips only. After that, hidden clips are included, so the
     * other devices learn of a delete. Ordered by (updatedAt, id); the sort on the pageable is ignored.
     */
    Page<ClipChange> findClipChangesSince(Long ownerId, LocalDateTime since, Pageable pageable);

    /**
     * The person deleted one clip (a sync batch CLIP_DELETE). The clip is hidden and marked removed,
     * so it never comes back with its note, and its audio file is removed. Throws 404 for a clip that
     * is not theirs or is already gone: a replay of a delete is therefore a harmless 404.
     */
    void removeClip(Long ownerId, Long clipId);

    /** The clip's transcript changed: its next sync carries the new state. No ownership check; callers are trusted. */
    void touchClip(Long clipId);

    // ---------------------------------------------------------------------
    // Pre-authorized access (voiceshare module only).
    //
    // These take NO ownerId and perform NO ownership check. The caller must
    // have already established that the requester may read this voice note —
    // in practice, by finding a VoiceShare grant. Calling one of these from a
    // controller, or from anywhere that hasn't checked a grant, is a
    // privilege escalation: any authenticated user could read any voice
    // note's metadata by guessing an id.
    //
    // Mirrors NoteService's identically-labelled section exactly. Unlike
    // getAuthorizedNote, there is no "AndDeletedFalse" half to this: VoiceNote
    // has no soft-delete flag, so a missing row here always means hard-deleted
    // or never existed, and 404 is the correct answer either way.
    //
    // The "authorized" in each name is a claim the CALLER makes, not
    // something these methods verify.
    // ---------------------------------------------------------------------

    /** Reads a voice note's metadata by id, ownership unchecked. Throws 404 if it doesn't exist. */
    VoiceNoteResponse getAuthorizedVoiceNote(Long voiceNoteId);

    /**
     * Loads a voice note's audio bytes, ownership unchecked. The
     * pre-authorized counterpart to {@link #loadAudio}, backing the
     * recipient-side stream/download endpoints (voiceshare module) the same
     * way {@link #getAuthorizedVoiceNote} backs the recipient-side metadata
     * read. Added alongside SharedVoiceNoteService rather than in the earlier
     * accessor batch, since audio loading is a distinct concern (storage I/O,
     * not a repository read) from the metadata accessors above.
     */
    VoiceNoteContent getAuthorizedAudio(Long voiceNoteId);

    /**
     * Resolves a batch of voice note ids to metadata in one query, ownership
     * unchecked. Keyed by voice note id, so callers can look up each row's
     * voice note without depending on result order.
     *
     * <p>Ids that are unknown are simply absent from the map — the caller
     * decides whether that's a skipped row or an error, same contract as
     * NoteService#getAuthorizedNoteSummaries.
     */
    Map<Long, VoiceNoteResponse> getAuthorizedVoiceNoteSummaries(Collection<Long> voiceNoteIds);
    /**
     * Every voice note attached to a note, ownership of each individual
     * recording unchecked — only whether the CALLER may see the note at all
     * is the caller's job to verify first (SharedNoteService's requireGrant,
     * or NoteService's own ownership check for the note's owner).
     *
     * <p>Unlike {@link #getAuthorizedVoiceNoteSummaries}, which resolves a
     * caller-supplied batch of ids the caller already knows about, this
     * DISCOVERS the ids — it answers "what's attached to this note", not
     * "resolve these specific ids". That's why it takes a single noteId
     * rather than a Collection of voiceNoteIds.
     */
    List<VoiceNoteResponse> getAuthorizedVoiceNotesByNote(Long noteId);

    /**
     * Copies a clip into another note of the same caller, duplicating the stored object inside the
     * storage backend (no download, no re-upload) (step 17f, B-05). Idempotent on (caller, clientId).
     * The caller has already been checked: this method does not decide who may read the source.
     */
    VoiceUpload copyClip(Long userId, Long sourceVoiceNoteId, Long targetNoteId, String clientId);
}