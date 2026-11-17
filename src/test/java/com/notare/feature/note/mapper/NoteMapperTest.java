package com.notare.feature.note.mapper;

import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.note.model.Note;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3a of the coverage roadmap.
 *
 * <p>Covers {@link NoteMapper#toResponse}, {@link NoteMapper#toSummary}, and
 * the private preview-building logic exercised through {@code toSummary}:
 * null/blank content collapsing to a null preview, whitespace stripping, and
 * both edges of the 200-character truncation - a real off-by-one risk given
 * how close it is to {@link com.notare.common.util.NoteLabeler}'s own
 * 255-character truncation (Step 1b), but a DIFFERENT limit and a DIFFERENT
 * class, so it earns its own boundary tests rather than borrowing 1b's.
 */
class NoteMapperTest {

    private final NoteMapper mapper = new NoteMapper();

    @Test
    void toResponse_mapsEveryFieldFromTheEntity() {
        Note note = new Note();
        note.setId(7L);
        note.setOwnerId(3L);
        note.setContent("hi");
        note.setPinned(true);
        note.setVersion(2L);
        note.setLastModifiedBy(9L);

        NoteResponse response = mapper.toResponse(note);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.ownerId()).isEqualTo(3L);
        assertThat(response.content()).isEqualTo("hi");
        assertThat(response.pinned()).isTrue();
        assertThat(response.version()).isEqualTo(2L);
        assertThat(response.lastModifiedBy()).isEqualTo(9L);
    }

    @Test
    void toResponse_doesNotTruncateTheFullContent() {
        // Unlike the summary's preview, the detail response carries the
        // whole thing untouched, however long.
        String longContent = "x".repeat(500);
        Note note = new Note();
        note.setContent(longContent);

        NoteResponse response = mapper.toResponse(note);

        assertThat(response.content()).isEqualTo(longContent);
    }

    @Test
    void toSummary_nullContentProducesANullPreview() {
        Note note = new Note();
        note.setContent(null);

        NoteSummaryResponse summary = mapper.toSummary(note);

        assertThat(summary.contentPreview()).isNull();
    }

    @Test
    void toSummary_blankContentProducesANullPreview() {
        Note note = new Note();
        note.setContent("   \n  ");

        NoteSummaryResponse summary = mapper.toSummary(note);

        assertThat(summary.contentPreview()).isNull();
    }

    @Test
    void toSummary_shortContentIsStrippedOfSurroundingWhitespace() {
        Note note = new Note();
        note.setContent("  Hello world  ");

        NoteSummaryResponse summary = mapper.toSummary(note);

        assertThat(summary.contentPreview()).isEqualTo("Hello world");
    }

    @Test
    void toSummary_contentAtExactly200CharactersIsNotTruncated() {
        String exactly200 = "a".repeat(200);
        Note note = new Note();
        note.setContent(exactly200);

        NoteSummaryResponse summary = mapper.toSummary(note);

        assertThat(summary.contentPreview()).isEqualTo(exactly200);
        assertThat(summary.contentPreview()).hasSize(200);
    }

    @Test
    void toSummary_contentOver200CharactersIsTruncatedWithAnEllipsis() {
        // Unlike NoteLabeler's truncate() (Step 1b), which reserves space for
        // the ellipsis so the total stays AT the cap, this one appends the
        // ellipsis AFTER the 200 characters - so the result is 201 characters
        // long, not 200. Locking in the actual behavior, not the "nicer"
        // assumption.
        String chars201 = "b".repeat(201);
        Note note = new Note();
        note.setContent(chars201);

        NoteSummaryResponse summary = mapper.toSummary(note);

        assertThat(summary.contentPreview()).isEqualTo("b".repeat(200) + "…");
        assertThat(summary.contentPreview()).hasSize(201);
    }

    @Test
    void toSummary_mapsTheNonPreviewFieldsFromTheEntity() {
        Note note = new Note();
        note.setId(8L);
        note.setContent("summary test");
        note.setPinned(false);
        note.setVersion(5L);

        NoteSummaryResponse summary = mapper.toSummary(note);

        assertThat(summary.id()).isEqualTo(8L);
        assertThat(summary.pinned()).isFalse();
        assertThat(summary.version()).isEqualTo(5L);
    }
}