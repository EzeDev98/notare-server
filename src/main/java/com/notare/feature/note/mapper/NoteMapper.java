package com.notare.feature.note.mapper;

import com.notare.feature.note.dto.request.CreateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.note.model.Note;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper (the codebase doesn't use MapStruct). It's a @Component
 * so the service can inject it. Keeping mapping here means the service focuses
 * on business logic and the DTO<->entity translation lives in one place.
 */
@Component
public class NoteMapper {

    private static final int PREVIEW_MAX_LENGTH = 200;

    /**
     * Builds a brand-new Note from a create request. We only set the fields the
     * caller controls: ownerId, content. Everything else is left to defaults
     * or the framework — pinned/deleted default to false, and id,
     * version, createdAt, updatedAt are populated by JPA on save.
     */
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
                note.getUpdatedAt()
        );
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

    /**
     * Produces the short preview shown in list views. Null/blank content yields
     * null (nothing to preview); long content is cut to PREVIEW_MAX_LENGTH with
     * an ellipsis so the client can show "…" without guessing.
     */
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