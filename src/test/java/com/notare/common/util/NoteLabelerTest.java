package com.notare.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for NoteLabeler. */
class NoteLabelerTest {

    @Test
    void deriveLabel_nullContentReturnsEmptyNoteLabel() {
        assertThat(NoteLabeler.deriveLabel(null)).isEqualTo(NoteLabeler.EMPTY_NOTE_LABEL);
    }

    @Test
    void deriveLabel_emptyContentReturnsEmptyNoteLabel() {
        assertThat(NoteLabeler.deriveLabel("")).isEqualTo(NoteLabeler.EMPTY_NOTE_LABEL);
    }

    @Test
    void deriveLabel_whitespaceOnlyContentReturnsEmptyNoteLabel() {
        assertThat(NoteLabeler.deriveLabel("   ")).isEqualTo(NoteLabeler.EMPTY_NOTE_LABEL);
    }

    @Test
    void deriveLabel_whitespaceOnlyContentAcrossMultipleLinesReturnsEmptyNoteLabel() {
        assertThat(NoteLabeler.deriveLabel("   \n\t\n  ")).isEqualTo(NoteLabeler.EMPTY_NOTE_LABEL);
    }

    @Test
    void deriveLabel_singleLineContentIsReturnedAsIs() {
        assertThat(NoteLabeler.deriveLabel("Shopping list")).isEqualTo("Shopping list");
    }

    @Test
    void deriveLabel_leadingAndTrailingWhitespaceIsTrimmed() {
        assertThat(NoteLabeler.deriveLabel("   Shopping list   ")).isEqualTo("Shopping list");
    }

    @Test
    void deriveLabel_onlyTheFirstLineIsUsed() {
        assertThat(NoteLabeler.deriveLabel("Title Line\nBody text here")).isEqualTo("Title Line");
    }

    @Test
    void deriveLabel_blankLeadingLinesAreSkippedToFindTheFirstNonBlankLine() {
        assertThat(NoteLabeler.deriveLabel("\n   \nReal Title\nMore text")).isEqualTo("Real Title");
    }

    @Test
    void deriveLabel_aTabOnlyLineCountsAsBlankAndIsSkipped() {
        assertThat(NoteLabeler.deriveLabel("\t\nSecond")).isEqualTo("Second");
    }

    @Test
    void deriveLabel_windowsLineEndingsDoNotLeaveATrailingCarriageReturn() {
        assertThat(NoteLabeler.deriveLabel("Hello\r\nWorld")).isEqualTo("Hello");
    }

    @Test
    void deriveLabel_labelAtExactly255CharactersIsNotTruncated() {
        String exactly255 = "a".repeat(255);

        String result = NoteLabeler.deriveLabel(exactly255);

        assertThat(result).isEqualTo(exactly255);
        assertThat(result).hasSize(255);
    }

    @Test
    void deriveLabel_labelOver255CharactersIsTruncatedToFitWithAnEllipsis() {
        String over255 = "b".repeat(300);

        String result = NoteLabeler.deriveLabel(over255);

        assertThat(result).hasSize(255);
        assertThat(result).isEqualTo("b".repeat(254) + "…");
    }

    @Test
    void deriveLabel_truncationAppliesToTheSelectedLineNotTheRawContent() {
        String content = "\n  \n" + "c".repeat(260);

        String result = NoteLabeler.deriveLabel(content);

        assertThat(result).hasSize(255);
        assertThat(result).isEqualTo("c".repeat(254) + "…");
    }

    @Test
    void emptyNoteLabelConstant_isNewNote() {
        assertThat(NoteLabeler.EMPTY_NOTE_LABEL).isEqualTo("New Note");
    }
}
