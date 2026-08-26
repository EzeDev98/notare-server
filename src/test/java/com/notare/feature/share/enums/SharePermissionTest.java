package com.notare.feature.share.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 1c of the coverage roadmap.
 *
 * <p>Covers {@link SharePermission#satisfies(SharePermission)} - the single
 * method every access check in both the note-sharing and voice-sharing
 * modules calls to decide "is this grant strong enough." Its own doc comment
 * calls out two things that must hold: WRITE implies READ (but not the
 * other way around), and the constants must stay declared weakest-first
 * because the comparison is ordinal-based. This class tests both directly,
 * rather than trusting that nobody ever reorders the enum.
 */
class SharePermissionTest {

    @Test
    void satisfies_readSatisfiesRead() {
        assertThat(SharePermission.READ.satisfies(SharePermission.READ)).isTrue();
    }

    @Test
    void satisfies_writeSatisfiesWrite() {
        assertThat(SharePermission.WRITE.satisfies(SharePermission.WRITE)).isTrue();
    }

    @Test
    void satisfies_writeSatisfiesRead() {
        // The one rule the whole method exists to express: a WRITE grant is
        // also good enough for anything that only requires READ.
        assertThat(SharePermission.WRITE.satisfies(SharePermission.READ)).isTrue();
    }

    @Test
    void satisfies_readDoesNotSatisfyWrite() {
        // The other direction must NOT hold, or a read-only collaborator
        // could pass a write-gated check.
        assertThat(SharePermission.READ.satisfies(SharePermission.WRITE)).isFalse();
    }

    @Test
    void satisfies_everyPairMatchesOrdinalComparison() {
        // Property-level check standing in for the method's actual
        // implementation (this.ordinal() >= required.ordinal()) rather than
        // four more hand-picked cases - if a third level is ever added
        // (the class doc comment mentions COMMENT as a candidate), this
        // still passes as long as the constants stay weakest-first.
        for (SharePermission granted : SharePermission.values()) {
            for (SharePermission required : SharePermission.values()) {
                boolean expected = granted.ordinal() >= required.ordinal();
                assertThat(granted.satisfies(required))
                        .as("%s.satisfies(%s)", granted, required)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void constants_areDeclaredWeakestFirst() {
        // satisfies() is ordinal-based ONLY, with no persistence dependency
        // on the order (the column is @Enumerated(STRING)) - which makes it
        // easy to reorder these two constants without anything obviously
        // breaking at compile time. This is the guard rail: READ must stay
        // ordinal 0 and WRITE ordinal 1, or every access check in the app
        // silently inverts.
        assertThat(SharePermission.values()).containsExactly(SharePermission.READ, SharePermission.WRITE);
        assertThat(SharePermission.READ.ordinal()).isZero();
        assertThat(SharePermission.WRITE.ordinal()).isOne();
    }
}