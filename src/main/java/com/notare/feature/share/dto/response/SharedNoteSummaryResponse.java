package com.notare.feature.share.dto.response;

import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.share.enums.SharePermission;

import java.time.LocalDateTime;

/**
 * A row in "notes shared with me": the note itself, plus what I'm allowed to do
 * with it and who gave it to me.
 *
 * <p>The note is NESTED as the existing {@link NoteSummaryResponse} rather than
 * having its fields flattened in here. Two reasons. First, the note module then
 * owns the wire shape of a note in exactly one place — add a field to a note
 * summary and it appears here automatically, instead of being forgotten in a
 * parallel copy that slowly drifts. Second, the content preview logic
 * (truncation, ellipsis) already lives in NoteMapper and doesn't need a second
 * home.
 *
 * <p>The cost is a nested object in the JSON — {@code {"note": {...},
 * "permission": "READ"}} — which is a fair trade for one source of truth. It's
 * also honest about the structure: this row IS a note plus a grant.
 *
 * <p>This is the share module depending on the note module, which is the
 * direction Step 7 established. The reverse never happens.
 */
public record SharedNoteSummaryResponse(

        /** The grant's id, so the client can address it if it ever needs to. */
        Long shareId,

        /** The note, in the same shape the owner's own list endpoint returns. */
        NoteSummaryResponse note,

        /** Who owns it — the person who shared it with me. */
        Long ownerId,

        /** What I may do with it. The client uses this to decide whether to render an editor or a read-only view. */
        SharePermission permission,

        /** When it was shared with me. Distinct from the note's own updatedAt, which is inside {@code note}. */
        LocalDateTime sharedAt
) {
}