package com.notare.feature.user.service.impl;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.notare.common.constant.GeneralConstant.CONSONANTS;
import static com.notare.common.constant.GeneralConstant.LENGTH;
import static com.notare.common.constant.GeneralConstant.VOWELS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1e of the coverage roadmap.
 *
 * <p>Covers {@link NotarianIdGenerator#generateId()}: the fixed length, the
 * consonant/vowel/consonant/... alternating pattern, and that every
 * character drawn actually comes from {@link
 * com.notare.common.constant.GeneralConstant}'s pools rather than some
 * off-by-one slipping past {@code random.nextInt(pool.length)}.
 *
 * <p>The retry-on-collision loop (bounded by {@code
 * MAX_ID_GENERATION_ATTEMPTS}) lives in {@code UserServiceImplementation},
 * not here - this class only generates one candidate per call, so that
 * belongs to Step 5a's tests, not this one.
 *
 * <p>A note on the two sample sizes below: this class uses an internal,
 * unmockable {@code SecureRandom}, so there's no way to force a specific
 * output - the only honest way to test it is statistically. The
 * pattern/pool-membership check runs a large sample (2,000) because that
 * assertion can never fail on a correct implementation, however many draws
 * it makes. The distinctness check deliberately uses a much smaller sample
 * (100) because IT CAN legitimately fail even when the generator is
 * correct - the id space is only 16^4 * 5^3 = 8,192,000 combinations, and
 * the birthday paradox means a big enough sample will eventually collide by
 * chance. 100 draws keeps that false-failure probability under 0.1%
 * (empirically confirmed against this exact class: 0 collisions across 20
 * independent trials of 100 draws each before this test was written).
 */
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