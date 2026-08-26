package com.notare.common.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 4c of the coverage roadmap (part 2 of 2 - see also {@link
 * SecurityAuditorAwareTest}).
 *
 * <p>{@link SystemAuditor} is a one-constant holder, so there isn't much
 * behavior to test - but that constant is load-bearing across every audited
 * entity in the app (Note.lastModifiedBy, Collaboration.lastModifiedBy, and
 * anywhere else {@code @CreatedBy}/{@code @LastModifiedBy} is used), so
 * pinning down its exact value is worth a real test rather than trusting it
 * by inspection. Per the class's own doc comment, {@code 0} is chosen
 * specifically because IDENTITY sequences start at {@code 1} and can never
 * produce it - this test locks that invariant in too, not just the literal
 * value.
 */
class SystemAuditorTest {

    @Test
    void id_isZero() {
        assertThat(SystemAuditor.ID).isEqualTo(0L);
    }

    @Test
    void id_isBelowWhereIdentityGeneratedIdsStart() {
        // IDENTITY columns in this schema start at 1 (see e.g. User, Note,
        // Collaboration). SystemAuditor.ID must stay below that range or it
        // could eventually collide with a real user's id.
        assertThat(SystemAuditor.ID).isLessThan(1L);
    }
}