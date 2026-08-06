package com.notare.feature.voicenote.mapper;

import com.notare.feature.voicenote.model.VoiceNote;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper (matching the codebase convention). toEntity takes plain
 * values, not a MultipartFile, so the mapper stays free of any web dependency —
 * the service extracts the file's fields and passes them in.
 */
@Component
public class VoiceNoteMapper {

    public VoiceNote toEntity(Long ownerId, Long noteId, String originalFilename,
                              String contentType, String storageKey,
                              long sizeBytes, Integer durationSeconds) {
        VoiceNote vn = new VoiceNote();
        vn.setOwnerId(ownerId);
        vn.setNoteId(noteId);
        vn.setOriginalFilename(originalFilename);
        vn.setContentType(contentType);
        vn.setStorageKey(storageKey);
        vn.setSizeBytes(sizeBytes);
        vn.setDurationSeconds(durationSeconds);
        return vn;
    }

    public VoiceNoteResponse toResponse(VoiceNote voiceNote) {
        return new VoiceNoteResponse(
                voiceNote.getId(),
                voiceNote.getOwnerId(),
                voiceNote.getNoteId(),
                voiceNote.getOriginalFilename(),
                voiceNote.getContentType(),
                voiceNote.getSizeBytes(),
                voiceNote.getDurationSeconds(),
                voiceNote.getCreatedAt(),
                voiceNote.getUpdatedAt()
        );
    }
}