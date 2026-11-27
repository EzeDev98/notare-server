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

/** Stores, reads and deletes a user's voice clips. */
public interface VoiceNoteService {

    VoiceNoteResponse upload(Long ownerId, MultipartFile file, Long noteId, Integer durationSeconds);

    VoiceNoteResponse getVoiceNote(Long ownerId, Long voiceNoteId);

    PagedResponse<VoiceNoteResponse> listVoiceNotes(Long ownerId, Pageable pageable);

    List<VoiceNoteResponse> listVoiceNotesForNote(Long ownerId, Long noteId);

    VoiceNoteContent loadAudio(Long ownerId, Long voiceNoteId);

    void deleteVoiceNote(Long ownerId, Long voiceNoteId);

    Page<ClipChange> findClipChangesSince(Long ownerId, LocalDateTime since, Pageable pageable);

    void removeClip(Long ownerId, Long clipId);

    void touchClip(Long clipId);

    boolean attachStandaloneClip(Long ownerId, Long clipId, Long noteId);
}
