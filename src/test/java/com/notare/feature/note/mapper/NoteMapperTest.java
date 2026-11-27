package com.notare.feature.note.mapper;

import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.note.model.Note;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for NoteMapper. */
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
