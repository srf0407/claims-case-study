package com.claimsprocessing.claims.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClaimWorkerResultMapperTest {

    private final ClaimWorkerResultMapper mapper = new ClaimWorkerResultMapper();

    @Test
    void should_map_payment_initiated_and_sent_to_analyst() {
        // given / when / then
        assertThat(mapper.map(StraightThroughResult.PAYMENT_INITIATED))
                .isEqualTo(ClaimWorkerResult.PAYMENT_INITIATED);
        assertThat(mapper.map(StraightThroughResult.SENT_TO_ANALYST))
                .isEqualTo(ClaimWorkerResult.SENT_TO_ANALYST);
    }

    @Test
    void should_reject_a_null_straight_through_result() {
        // given / when / then
        assertThatThrownBy(() -> mapper.map(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("straightThroughResult");
    }
}
