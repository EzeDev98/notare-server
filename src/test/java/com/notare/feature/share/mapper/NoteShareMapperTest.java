package com.notare.feature.share.mapper;

import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.share.dto.response.NoteShareResponse;
import com.notare.feature.share.dto.response.SharedNoteSummaryResponse;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.model.NoteShare;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3b of the coverage roadmap.
 *
 * <p>Covers {@link NoteShareMapper#toResponse} and {@link
 * NoteShareMapper#toSharedNote}. The second one is the more interesting of
 * the two: its own doc comment says the mapper must stay free of repository
 * calls and just join whatever {@code NoteSummaryResponse} it's handed - so
 * this test asserts the note is passed through BY REFERENCE (proving the
 * mapper isn't quietly re-deriving or copying it), and that a null note is
 * tolerated rather than throwing, since the caller (SharedNoteServiceImpl)
 * relies on that to drop stale rows instead of crashing.
 */
class NoteShareMapperTest {

    private final NoteShareMapper mapper = new NoteShareMapper();

    @Test
    void toResponse_mapsEveryFieldFromTheGrant() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 1, 2, 11, 0);

        NoteShare share = new NoteShare();
        share.setId(1L);
        share.setNoteId(2L);
        share.setOwnerId(3L);
        share.setSharedWithUserId(4L);
        share.setPermission(SharePermission.WRITE);
        share.setCreatedAt(created);
        share.setUpdatedAt(updated);

        NoteShareResponse response = mapper.toResponse(share);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.noteId()).isEqualTo(2L);
        assertThat(response.ownerId()).isEqualTo(3L);
        assertThat(response.sharedWithUserId()).isEqualTo(4L);
        assertThat(response.permission()).isEqualTo(SharePermission.WRITE);
        assertThat(response.createdAt()).isEqualTo(created);
        assertThat(response.updatedAt()).isEqualTo(updated);
    }

    @Test
    void toSharedNote_joinsTheGrantWithTheSuppliedNoteSummary() {
        LocalDateTime sharedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        NoteShare share = new NoteShare();
        share.setId(1L);
        share.setOwnerId(3L);
        share.setPermission(SharePermission.READ);
        share.setCreatedAt(sharedAt);

        NoteSummaryResponse note = new NoteSummaryResponse(2L, "preview", false, false, 1L, sharedAt);

        SharedNoteSummaryResponse result = mapper.toSharedNote(share, note);

        assertThat(result.shareId()).isEqualTo(1L);
        assertThat(result.ownerId()).isEqualTo(3L);
        assertThat(result.permission()).isEqualTo(SharePermission.READ);
        // sharedAt comes from the GRANT's createdAt, not the note's own updatedAt.
        assertThat(result.sharedAt()).isEqualTo(sharedAt);
    }

    @Test
    void toSharedNote_passesTheSuppliedNoteThroughByReference() {
        // The mapper must not re-fetch or copy the note - it only joins
        // whatever it was handed, which is what keeps it free of repository
        // calls (and the N+1 that would come with one).
        NoteShare share = new NoteShare();
        share.setId(1L);
        share.setPermission(SharePermission.READ);
        NoteSummaryResponse note = new NoteSummaryResponse(2L, "preview", false, false, 1L, null);

        SharedNoteSummaryResponse result = mapper.toSharedNote(share, note);

        assertThat(result.note()).isSameAs(note);
    }

    @Test
    void toSharedNote_toleratesANullNoteWithoutThrowing() {
        // SharedNoteServiceImpl relies on this: a grant whose note has since
        // been deleted maps to a row with a null note, which the SERVICE then
        // drops - the mapper itself must not throw or substitute a default.
        NoteShare share = new NoteShare();
        share.setId(1L);
        share.setPermission(SharePermission.READ);

        SharedNoteSummaryResponse result = mapper.toSharedNote(share, null);

        assertThat(result.note()).isNull();
        assertThat(result.shareId()).isEqualTo(1L);
    }
}