package com.notare.feature.user.service.impl;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.notare.common.constant.GeneralConstant.CONSONANTS;
import static com.notare.common.constant.GeneralConstant.LENGTH;
import static com.notare.common.constant.GeneralConstant.VOWELS;
import static org.assertj.core.api.Assertions.assertThat;

/** Tests for NotarianIdGenerator. */
class NotarianIdGeneratorTest {

    private static final int LARGE_SAMPLE = 2000;
    private static final int DISTINCTNESS_SAMPLE = 100;

    @Test
    void generateId_hasTheConfiguredLength() {
        NotarianIdGenerator generator = new NotarianIdGenerator();

        assertThat(generator.generateId()).hasSize(LENGTH);
    }

    @Test
    void generateId_alwaysAlternatesConsonantAndVowelStartingAndEndingOnAConsonant() {
        NotarianIdGenerator generator = new NotarianIdGenerator();

        for (int i = 0; i < LARGE_SAMPLE; i++) {
            String id = generator.generateId();
            for (int pos = 0; pos < id.length(); pos++) {
                char[] expectedPool = (pos % 2 == 0) ? CONSONANTS : VOWELS;
                assertThat(expectedPool)
                        .as("character at position %d of id '%s' should come from %s",
                                pos, id, pos % 2 == 0 ? "CONSONANTS" : "VOWELS")
                        .contains(id.charAt(pos));
            }
        }
    }

    @Test
    void generateId_neverProducesACharacterOutsideTheConfiguredPools() {
        NotarianIdGenerator generator = new NotarianIdGenerator();
        Set<Character> allowed = new HashSet<>();
        for (char c : CONSONANTS) allowed.add(c);
        for (char c : VOWELS) allowed.add(c);

        for (int i = 0; i < LARGE_SAMPLE; i++) {
            String id = generator.generateId();
            for (char c : id.toCharArray()) {
                assertThat(allowed).as("unexpected character '%s' in id '%s'", c, id).contains(c);
            }
        }
    }

    @Test
    void generateId_repeatedCallsProduceDistinctIdsAtASmallSampleSize() {
        NotarianIdGenerator generator = new NotarianIdGenerator();
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < DISTINCTNESS_SAMPLE; i++) {
            ids.add(generator.generateId());
        }

        assertThat(ids).hasSize(DISTINCTNESS_SAMPLE);
    }
}
