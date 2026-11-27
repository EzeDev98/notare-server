package com.notare.common.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for SystemAuditor. */
class SystemAuditorTest {

    @Test
    void id_isZero() {
        assertThat(SystemAuditor.ID).isEqualTo(0L);
    }

    @Test
    void id_isBelowWhereIdentityGeneratedIdsStart() {
        assertThat(SystemAuditor.ID).isLessThan(1L);
    }
}
