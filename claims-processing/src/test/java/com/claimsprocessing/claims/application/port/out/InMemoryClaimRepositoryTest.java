package com.claimsprocessing.claims.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.claimsprocessing.claims.domain.ClaimId;
import org.junit.jupiter.api.Test;

class InMemoryClaimRepositoryTest {

    @Test
    void should_return_empty_for_an_unknown_id() {
        // given
        var claims = new InMemoryClaimRepository();
        var id = new ClaimId(UUID.randomUUID());

        // when
        var found = claims.findById(id);

        // then
        assertThat(found).isEmpty();
    }
}
