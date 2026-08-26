package com.notare.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1a of the coverage roadmap.
 *
 * <p>Covers {@link LikeEscaper#escape(String)} against its own doc comment:
 * null/empty pass through untouched, each of the three characters that are
 * special to a LIKE pattern ({@code %}, {@code _}, and the {@code '!'}
 * escape character itself) gets neutralised, and — the one place this is
 * easy to get backwards — the escape character is escaped BEFORE the
 * wildcards, so a term that already contains a literal {@code !} doesn't
 * get corrupted by the passes that follow it.
 */
class LikeEscaperTest {

    @Test
    void escape_nullPassesThroughUnchanged() {
        assertThat(LikeEscaper.escape(null)).isNull();
    }

    @Test
    void escape_emptyStringPassesThroughUnchanged() {
        assertThat(LikeEscaper.escape("")).isEmpty();
    }

    @Test
    void escape_blankWhitespacePassesThroughUnchanged() {
        // Not caught by the null/empty short-circuit (a space isn't empty),
        // but there's nothing in it to escape either, so the result is the
        // same term back out. Locks in the doc comment's "blank... passes
        // through unchanged" claim regardless of which code path gets there.
        assertThat(LikeEscaper.escape(" ")).isEqualTo(" ");
    }

    @Test
    void escape_termWithNoSpecialCharactersIsUnchanged() {
        assertThat(LikeEscaper.escape("groceries")).isEqualTo("groceries");
    }

    @Test
    void escape_singlePercentBecomesEscapedPercent() {
        // The exact bug from the class doc comment: an unescaped '%' would
        // turn '%term%' into '%%%', matching every row.
        assertThat(LikeEscaper.escape("%")).isEqualTo("!%");
    }

    @Test
    void escape_singleUnderscoreBecomesEscapedUnderscore() {
        // Unescaped, '_' matches any one character - "snake_case" would also
        // match "snakeXcase".
        assertThat(LikeEscaper.escape("_")).isEqualTo("!_");
    }

    @Test
    void escape_snakeCaseExampleFromDocComment() {
        assertThat(LikeEscaper.escape("snake_case")).isEqualTo("snake!_case");
    }

    @Test
    void escape_literalEscapeCharacterIsDoubled() {
        // An unpaired '!' is undefined behaviour in some dialects, so a
        // literal '!' in the input must come out as a matched pair.
        assertThat(LikeEscaper.escape("!")).isEqualTo("!!");
    }

    @Test
    void escape_multipleOccurrencesOfTheSameWildcardAreAllEscaped() {
        assertThat(LikeEscaper.escape("a%b%c")).isEqualTo("a!%b!%c");
    }

    @Test
    void escape_consecutiveWildcardsAreEachEscapedIndependently() {
        assertThat(LikeEscaper.escape("%%")).isEqualTo("!%!%");
    }

    @Test
    void escape_escapeCharacterBeforeWildcard_orderingDoesNotCorruptOutput() {
        // '!' is escaped in the first pass ('!' -> '!!'), THEN '%' is escaped
        // in the second pass. Doing it in the other order would turn the '!'
        // introduced by escaping '%' into a second, unintended escape.
        assertThat(LikeEscaper.escape("!%")).isEqualTo("!!!%");
    }

    @Test
    void escape_wildcardBeforeEscapeCharacter_orderingDoesNotCorruptOutput() {
        assertThat(LikeEscaper.escape("%!")).isEqualTo("!%!!");
    }

    @Test
    void escape_allThreeSpecialCharactersTogether() {
        assertThat(LikeEscaper.escape("!_%")).isEqualTo("!!!_!%");
    }

    @Test
    void escape_realisticSearchTermWithMixedSpecialCharacters() {
        assertThat(LikeEscaper.escape("50%_off!")).isEqualTo("50!%!_off!!");
    }

    @Test
    void escapeCharacterConstant_isExclamationMark() {
        // The ESCAPE '!' clause on every LIKE query must match this constant
        // exactly - if the two drift apart, escaping silently stops working
        // rather than failing loudly.
        assertThat(LikeEscaper.ESCAPE_CHARACTER).isEqualTo("!");
    }
}