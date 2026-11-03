package com.claimsprocessing.claims.application.port.out;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;
import org.junit.jupiter.api.Test;

class ClaimQueuedMessageTest {

    @Test
    void should_reject_a_null_claim_id() {
        // given / when / then
        assertThatThrownBy(() -> new ClaimQueuedMessage(null, ClaimType.FUNERAL))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimId");
    }

    @Test
    void should_reject_a_null_claim_type() {
        // given
        var claimId = new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        // when / then
        assertThatThrownBy(() -> new ClaimQueuedMessage(claimId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimType");
    }
}
