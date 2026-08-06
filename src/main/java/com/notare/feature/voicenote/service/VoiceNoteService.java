package com.notare.feature.voicenote.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

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
     * Loads the audio bytes (ownership-scoped) plus the metadata needed to serve
     * them. Backs both the download and stream endpoints.
     */
    VoiceNoteContent loadAudio(Long ownerId, Long voiceNoteId);

    void deleteVoiceNote(Long ownerId, Long voiceNoteId);
}