package com.notare.feature.voicenote.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

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

    VoiceNoteResponse getVoiceNote(Long ownerId, Long voiceNoteId);

    PagedResponse<VoiceNoteResponse> listVoiceNotes(Long ownerId, Pageable pageable);

    List<VoiceNoteResponse> listVoiceNotesForNote(Long ownerId, Long noteId);

    /**
     * Loads the audio bytes (ownership-scoped) plus the metadata needed to serve
     * them. Backs both the download and stream endpoints.
     */
    VoiceNoteContent loadAudio(Long ownerId, Long voiceNoteId);

    void deleteVoiceNote(Long ownerId, Long voiceNoteId);

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
}