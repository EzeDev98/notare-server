package com.notare.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1b of the coverage roadmap.
 *
 * <p>Covers {@link NoteLabeler#deriveLabel(String)}: the empty-content
 * fallback, first-non-blank-line selection (and that only the FIRST line is
 * ever used, never the rest of the content), per-line trimming, and the
 * 255-character truncation the client's own version deliberately doesn't do.
 *
 * <p>One thing worth knowing while reading this class: the "every line was
 * blank" branch inside {@code deriveLabel}'s loop (its own comment gives
 * {@code "\n   \n"} as the example) is not actually reachable. {@code
 * String.trim()} only strips characters {@code <= U+0020}, so if the
 * whole-string {@code trim()} produced anything non-empty, that result's
 * first character is by definition non-whitespace - and that character
 * always belongs to the FIRST line the loop checks. So the loop can never
 * run out of lines once {@code trimmed} is non-empty; the whitespace-only
 * case is always caught by the earlier {@code trimmed.isEmpty()} check
 * instead. {@link #deriveLabel_whitespaceOnlyContentAcrossMultipleLinesReturnsEmptyNoteLabel()}
 * below tests that observable outcome, which is what actually matters here,
 * not which branch produces it.
 */
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
        // Blank lines, blank content, and a trailing newline - nothing here
        // is a real line, so this must resolve the same as truly empty
        // content. See the class-level note on why this is caught before
        // the line-scanning loop ever runs, not by it.
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
        // Must not concatenate, must not use a later line - the label is the
        // first line and nothing else, no matter how much text follows it.
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
        // split("\n") leaves a trailing '\r' attached to a CRLF first line;
        // the per-line trim() must strip it so the label doesn't end with an
        // invisible stray character.
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
        // Leading blank lines push the real (over-length) line off position
        // zero in the raw content; truncation must still land on exactly
        // 255 characters of the SELECTED line, unaffected by what preceded it.
        String content = "\n  \n" + "c".repeat(260);

        String result = NoteLabeler.deriveLabel(content);

        assertThat(result).hasSize(255);
        assertThat(result).isEqualTo("c".repeat(254) + "…");
    }

    @Test
    void emptyNoteLabelConstant_isNewNote() {
        // Must match the client's EMPTY_NOTE_LABEL wording exactly - this is
        // the value that lands in a notification when the note itself has no
        // content to summarize.
        assertThat(NoteLabeler.EMPTY_NOTE_LABEL).isEqualTo("New Note");
    }
}