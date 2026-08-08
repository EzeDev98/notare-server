package com.notare.feature.share.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.share.dto.response.SharedNoteSummaryResponse;
import org.springframework.data.domain.Pageable;

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
    NoteResponse getSharedNote(Long userId, Long noteId);

    /** Edit a shared note's title and content. Requires WRITE. */
    NoteResponse updateSharedNote(Long userId, Long noteId, UpdateNoteRequest request);

    /**
     * Auto-save a shared note. Requires WRITE, exactly as the full update does.
     *
     * <p>The permission model does not change here and must not: PATCH is a
     * smaller write than PUT, not a weaker one. A READ recipient gets the same
     * 403 either way.
     */
    NoteResponse autoSaveSharedNote(Long userId, Long noteId, AutoSaveNoteRequest request);
}