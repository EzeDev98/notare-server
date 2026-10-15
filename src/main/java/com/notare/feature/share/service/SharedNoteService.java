package com.notare.feature.share.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.share.dto.response.SharedNoteDetailResponse;
import com.notare.feature.share.dto.response.SharedNoteSummaryResponse;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
     * A note-attachment's audio bytes, for stream/download. Requires at least
     * READ on the PARENT NOTE — not a VoiceShare grant on the voice note
     * itself. This is the deliberate distinction from
     * SharedVoiceNoteService#getAudio: that one gates a voice note shared
     * directly; this one gates a voice note reachable only because the note
     * it's attached to was shared.
     */
    VoiceNoteContent getAttachedVoiceNoteAudio(Long userId, Long noteId, Long voiceNoteId);
}