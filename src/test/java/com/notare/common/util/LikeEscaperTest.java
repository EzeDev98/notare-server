package com.notare.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for LikeEscaper. */
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
        assertThat(LikeEscaper.escape(" ")).isEqualTo(" ");
    }

    @Test
    void escape_termWithNoSpecialCharactersIsUnchanged() {
        assertThat(LikeEscaper.escape("groceries")).isEqualTo("groceries");
    }

    @Test
    void escape_singlePercentBecomesEscapedPercent() {
        assertThat(LikeEscaper.escape("%")).isEqualTo("!%");
    }

    @Test
    void escape_singleUnderscoreBecomesEscapedUnderscore() {
        assertThat(LikeEscaper.escape("_")).isEqualTo("!_");
    }

    @Test
    void escape_snakeCaseExampleFromDocComment() {
        assertThat(LikeEscaper.escape("snake_case")).isEqualTo("snake!_case");
    }

    @Test
    void escape_literalEscapeCharacterIsDoubled() {
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
        assertThat(LikeEscaper.ESCAPE_CHARACTER).isEqualTo("!");
    }
}
