package com.notare.feature.share.mapper;

import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.share.dto.response.NoteShareResponse;
import com.notare.feature.share.dto.response.SharedNoteDetailResponse;
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
 * NoteShareMapper#toSharedSummary}. The second one is the more interesting of
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
    void toSharedSummary_shapesTheRowForTheRecipient() {
        LocalDateTime sharedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime changedAt = LocalDateTime.of(2026, 1, 5, 9, 30);
        NoteShare share = new NoteShare();
        share.setId(1L);
        share.setOwnerId(3L);
        share.setPermission(SharePermission.READ);
        share.setCreatedAt(sharedAt);

        NoteSummaryResponse note = new NoteSummaryResponse(2L, "First line\nmore", true, true, 9L, changedAt);

        SharedNoteSummaryResponse result = mapper.toSharedSummary(share, note, "Ada Obi");

        assertThat(result.shareId()).isEqualTo(1L);
        assertThat(result.noteId()).isEqualTo(2L);
        assertThat(result.title()).isEqualTo("First line");
        assertThat(result.contentPreview()).isEqualTo("First line\nmore");
        assertThat(result.ownerName()).isEqualTo("Ada Obi");
        assertThat(result.role()).isEqualTo("viewer");
        // updatedAt is the NOTE's; sharedAt is the GRANT's createdAt.
        assertThat(result.updatedAt()).isEqualTo(changedAt);
        assertThat(result.sharedAt()).isEqualTo(sharedAt);
    }

    @Test
    void toSharedSummary_namesAWriteGrantEditor() {
        NoteShare share = new NoteShare();
        share.setId(1L);
        share.setPermission(SharePermission.WRITE);
        NoteSummaryResponse note = new NoteSummaryResponse(2L, "preview", false, false, 1L, null);

        assertThat(mapper.toSharedSummary(share, note, null).role()).isEqualTo("editor");
    }

    @Test
    void toDetail_carriesOnlyWhatARecipientNeeds() {
        LocalDateTime changedAt = LocalDateTime.of(2026, 1, 5, 9, 30);
        NoteShare share = new NoteShare();
        share.setId(1L);
        share.setPermission(SharePermission.WRITE);
        // Pinned, archived and version are set on the note on purpose: none may reach the recipient.
        NoteResponse note = new NoteResponse(2L, 3L, "Title\nBody text", true, true, 7L, 3L, changedAt, changedAt);

        SharedNoteDetailResponse result = mapper.toDetail(share, note, "Ada Obi");

        assertThat(result.id()).isEqualTo(2L);
        assertThat(result.title()).isEqualTo("Title");
        assertThat(result.body()).isEqualTo("Title\nBody text");
        assertThat(result.role()).isEqualTo("editor");
        assertThat(result.ownerName()).isEqualTo("Ada Obi");
        assertThat(result.updatedAt()).isEqualTo(changedAt);
    }
}
