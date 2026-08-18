package com.notare.feature.voiceshare.mapper;

import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voiceshare.dto.response.SharedVoiceNoteSummaryResponse;
import com.notare.feature.voiceshare.dto.response.VoiceShareResponse;
import com.notare.feature.voiceshare.model.VoiceShare;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper, @Component, same as every other module here.
 *
 * <p>Mirrors {@link com.notare.feature.share.mapper.NoteShareMapper}.
 */
@Component
public class VoiceShareMapper {

    /** Owner-side view of a grant. */
    public VoiceShareResponse toResponse(VoiceShare share) {
        return new VoiceShareResponse(
                share.getId(),
                share.getVoiceNoteId(),
                share.getOwnerId(),
                share.getSharedWithUserId(),
                share.getPermission(),
                share.getCreatedAt(),
                share.getUpdatedAt()
        );
    }

    /**
     * Recipient-side view: a grant joined with the voice note it points at.
     * Mirrors {@code NoteShareMapper.toSharedNote}, but FLATTENED per
     * SharedVoiceNoteSummaryResponse's own field-by-field shape rather than
     * nesting a VoiceNoteResponse — see that record's doc comment for why.
     */
    public SharedVoiceNoteSummaryResponse toSharedVoiceNote(VoiceShare share, VoiceNoteResponse voiceNote) {
        return new SharedVoiceNoteSummaryResponse(
                share.getId(),
                voiceNote.id(),
                voiceNote.noteId(),
                share.getOwnerId(),
                share.getPermission(),
                voiceNote.originalFilename(),
                voiceNote.contentType(),
                voiceNote.sizeBytes(),
                voiceNote.durationSeconds(),
                voiceNote.createdAt(),
                share.getCreatedAt()
        );
    }
}
