package com.notare.feature.note.mapper;

import com.notare.feature.note.dto.request.CreateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.note.model.Note;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Maps notes to their responses. */
@Component
public class NoteMapper {

    private static final int PREVIEW_MAX_LENGTH = 200;

    public Note toEntity(Long ownerId, CreateNoteRequest request) {
        Note note = new Note();
        note.setOwnerId(ownerId);
        note.setContent(request.getContent());
        return note;
    }

    public NoteResponse toResponse(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getOwnerId(),
                note.getContent(),
                note.isPinned(),
                note.getVersion(),
                note.getLastModifiedBy(),
                note.getCreatedAt(),
                note.getUpdatedAt(),
                isRestorable(note),
                note.getAiTitle()
        );
    }

    public boolean isRestorable(Note note) {
        return note.getPreTidyContent() != null && Objects.equals(note.getVersion(), note.getTidiedVersion());
    }

    public NoteSummaryResponse toSummary(Note note) {
        return new NoteSummaryResponse(
                note.getId(),
                buildPreview(note.getContent()),
                note.isPinned(),
                note.getVersion(),
                note.getUpdatedAt()
        );
    }

    private String buildPreview(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.strip();
        if (trimmed.length() <= PREVIEW_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, PREVIEW_MAX_LENGTH) + "…";
    }
}
