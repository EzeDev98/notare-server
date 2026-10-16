package com.notare.feature.share.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.share.dto.response.SharedNoteDetailResponse;
import com.notare.feature.share.dto.response.SharedNoteSummaryResponse;
import com.notare.feature.share.enums.NoteAccess;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.dto.response.VoiceUpload;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * The RECIPIENT's side of sharing: what I can see and do with notes other
 * people have shared with me.
 *
 * <p>Deliberately the mirror of NoteShareService, and deliberately not merged
 * with it. Both classes deal in NoteShare rows, but they answer to different
 * people and enforce different questions: the owner's service asks "is this
 * your note?", this one asks "do you hold a grant, and is it strong enough for
 * what you're attempting?". Two authorization models in one class is how the
 * wrong one eventually gets applied.
 *
 * <p>Every method takes the acting user's id and every one resolves a grant
 * before touching a note. No path through this service reaches note data
 * without a grant lookup first.
 */
public interface SharedNoteService {

    /** Notes other people have shared with me. */
    PagedResponse<SharedNoteSummaryResponse> listSharedWithMe(Long userId, Pageable pageable);

    /** Open one shared note in full. Requires at least READ. */
    SharedNoteDetailResponse getSharedNote(Long userId, Long noteId);

    /** Edit a shared note's title and content. Requires WRITE. */
    SharedNoteDetailResponse updateSharedNote(Long userId, Long noteId, UpdateNoteRequest request);

    /**
     * Auto-save a shared note. Requires WRITE, exactly as the full update does.
     *
     * <p>The permission model does not change here and must not: PATCH is a
     * smaller write than PUT, not a weaker one. A READ recipient gets the same
     * 403 either way.
     */
    SharedNoteDetailResponse autoSaveSharedNote(Long userId, Long noteId, AutoSaveNoteRequest request);

    /**
     * Every voice note attached to a shared note, regardless of which
     * collaborator recorded each one. Requires at least READ — same grant a
     * recipient needs to open the note at all, since attachments are part of
     * the note's content, not a separately-permissioned thing.
     */
    List<VoiceNoteResponse> listAttachedVoiceNotes(Long userId, Long noteId);

    /**
     * Record a new voice note directly onto a shared note. Requires WRITE —
     * same bar as editing the note's text, since a voice reply is a form of
     * editing the note's content.
     *
     * <p>The resulting voice note is owned by the RECIPIENT who recorded it,
     * not the note's owner — same ownership model VoiceNoteService already
     * uses for the note owner's own recordings. A VoiceShare grant back to
     * the note's owner is created automatically, so the owner can hear it
     * the same way any other voice share works, without the recipient having
     * to separately share it back by hand.
     */
    VoiceNoteResponse uploadVoiceNote(Long userId, Long noteId, MultipartFile file, Integer durationSeconds);

    /**
     * Records a voice clip onto a note, for the note's owner or anyone with WRITE (step 17b, B-09).
     * One path for both, idempotent on {@code clientId}; it replaces the two older upload endpoints.
     * No access at all is a 404, a READ grant is a 403, as everywhere else in this service. When an
     * Editor records a new clip, the owner is granted READ on it, as {@link #uploadVoiceNote} does.
     */
    VoiceUpload uploadVoiceNoteToNote(Long userId, Long noteId, MultipartFile file, String clientId,
                                      Integer durationSeconds);

    /**
     * A note-attachment's audio bytes, for stream/download. Requires at least
     * READ on the PARENT NOTE — not a VoiceShare grant on the voice note
     * itself. This is the deliberate distinction from
     * SharedVoiceNoteService#getAudio: that one gates a voice note shared
     * directly; this one gates a voice note reachable only because the note
     * it's attached to was shared.
     */
    VoiceNoteContent getAttachedVoiceNoteAudio(Long userId, Long noteId, Long voiceNoteId);

    /**
     * What this user may do with a note (step 17d): OWNER for the owner of a note that is not in the
     * trash, EDITOR or VIEWER for a grant on one, and empty for everyone else, including a grant on a
     * trashed note. Other modules ask this instead of reading shares themselves.
     */
    Optional<NoteAccess> accessTo(Long userId, Long noteId);

    /**
     * The recipient removes their own grant (step 17f, B-04). Only the grantee can: the grant is
     * looked up by (note, caller), so anyone else gets 404. Publishes NoteShareRevokedEvent so the
     * live channel closes and the clips shared with them are cleaned up.
     */
    void leaveNote(Long userId, Long noteId);

    /**
     * Copies a clip into one of the caller's own notes (step 17f, B-05). The caller must be able to
     * read the source (owner, holder of a grant on its note, or holder of a share on the clip) and
     * must own the target note. Idempotent on (caller, clientId).
     */
    VoiceUpload copyVoiceNote(Long userId, Long voiceNoteId, Long targetNoteId, String clientId);
}