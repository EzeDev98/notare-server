package com.notare.feature.voicenote.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.ClipChange;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * Step 18e (legacy migration): moves the owner's standalone clip into a note. Returns false when
     * the clip is not theirs or already belongs to a note, so a repeat is harmless. The caller has
     * already made sure the note is the owner's.
     */
    boolean attachStandaloneClip(Long ownerId, Long clipId, Long noteId);
}