package com.claimsprocessing.claims.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.claimsprocessing.claims.application.port.in.ClaimDecision;
import com.claimsprocessing.claims.application.port.in.DecideClaimCommand;
import com.claimsprocessing.claims.domain.ClaimId;
import org.junit.jupiter.api.Test;

class DecideClaimMapperTest {

    private static final UUID CLAIM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final DecideClaimMapper mapper = new DecideClaimMapper();

    @Test
    void should_map_approve_and_reject() {
        // given
        var approve = new DecideClaimRequest(DecideClaimRequest.Decision.APPROVE);
        var reject = new DecideClaimRequest(DecideClaimRequest.Decision.REJECT);

        // when
        DecideClaimCommand approved = mapper.toCommand(CLAIM_ID, approve);
        DecideClaimCommand rejected = mapper.toCommand(CLAIM_ID, reject);

        // then
        assertThat(approved).isEqualTo(new DecideClaimCommand(new ClaimId(CLAIM_ID), ClaimDecision.APPROVE));
        assertThat(rejected).isEqualTo(new DecideClaimCommand(new ClaimId(CLAIM_ID), ClaimDecision.REJECT));
    }

    @Test
    void should_reject_a_null_request() {
        // given / when / then
        assertThatThrownBy(() -> mapper.toCommand(CLAIM_ID, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request");
    }
}
