package com.notare.feature.share.mapper;

import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.share.dto.response.NoteShareResponse;
import com.notare.feature.share.dto.response.SharedNoteSummaryResponse;
import com.notare.feature.share.model.NoteShare;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper, @Component, same as every other module here.
 */
@Component
public class NoteShareMapper {

    /** Owner-side view of a grant. */
    public NoteShareResponse toResponse(NoteShare share) {
        return new NoteShareResponse(
                share.getId(),
                share.getNoteId(),
                share.getOwnerId(),
                share.getSharedWithUserId(),
                share.getPermission(),
                share.getCreatedAt(),
                share.getUpdatedAt()
        );
    }

    /**
     * Recipient-side view: a grant joined with its note.
     *
     * <p>The note summary is passed IN rather than fetched here. Mappers stay
     * free of repository calls — a mapper that loads data would turn a loop over
     * a page of grants into a query per row, which is the N+1 this design has
     * been avoiding since Step 6. The caller (7D) batch-loads every note for the
     * page in one query and hands each summary down.
     *
     * <p>A null summary is legal and means the note is gone or trashed. The
     * mapper doesn't decide what that means — 7D drops those rows from the
     * listing rather than showing a hole.
     */
    public SharedNoteSummaryResponse toSharedNote(NoteShare share, NoteSummaryResponse note) {
        return new SharedNoteSummaryResponse(
                share.getId(),
                note,
                share.getOwnerId(),
                share.getPermission(),
                share.getCreatedAt()
        );
    }
}