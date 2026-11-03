package com.claimsprocessing.claims.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimsprocessing.claims.application.port.in.PaymentOutcome;
import com.claimsprocessing.claims.domain.ClaimStatus;
import org.junit.jupiter.api.Test;

class PaymentOutcomeMapperTest {

    private final PaymentOutcomeMapper mapper = new PaymentOutcomeMapper();

    @Test
    void should_map_paid_and_failed_onto_claim_statuses() {
        // given / when / then
        assertThat(mapper.map(PaymentOutcome.PAID)).isEqualTo(ClaimStatus.PAID);
        assertThat(mapper.map(PaymentOutcome.FAILED)).isEqualTo(ClaimStatus.PAYMENT_FAILED);
    }
}
