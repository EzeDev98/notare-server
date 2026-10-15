package com.notare.feature.share.mapper;

import com.notare.common.util.NoteLabeler;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.share.dto.response.NoteShareResponse;
import com.notare.feature.share.dto.response.SharedNoteDetailResponse;
import com.notare.feature.share.dto.response.SharedNoteSummaryResponse;
import com.notare.feature.share.enums.SharePermission;
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
     * Recipient-side row: a grant joined with its note, shaped for the person it was
     * shared with (step 15g). The note summary is passed in rather than fetched, so a
     * page of grants costs one batch query, not one per row. Pinned, archived and
     * version stay on the summary and are not copied across.
     */
    public SharedNoteSummaryResponse toSharedSummary(NoteShare share, NoteSummaryResponse note, String ownerName) {
        return new SharedNoteSummaryResponse(
                share.getId(),
                note.id(),
                NoteLabeler.deriveLabel(note.contentPreview()),
                note.contentPreview(),
                ownerName,
                roleOf(share.getPermission()),
                note.updatedAt(),
                share.getCreatedAt()
        );
    }

    /** Recipient-side detail: only id, title, body, role, owner name and updatedAt (step 15g). */
    public SharedNoteDetailResponse toDetail(NoteShare share, NoteResponse note, String ownerName) {
        return new SharedNoteDetailResponse(
                note.id(),
                NoteLabeler.deriveLabel(note.content()),
                note.content(),
                roleOf(share.getPermission()),
                ownerName,
                note.updatedAt()
        );
    }

    /** The same words the live editing session uses: "editor" or "viewer". */
    private static String roleOf(SharePermission permission) {
        return permission.satisfies(SharePermission.WRITE) ? "editor" : "viewer";
    }
}
