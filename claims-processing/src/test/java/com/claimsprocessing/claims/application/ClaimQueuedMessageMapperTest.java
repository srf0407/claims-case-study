package com.claimsprocessing.claims.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.claimsprocessing.claims.application.port.out.ClaimQueuedMessage;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimType;
import org.junit.jupiter.api.Test;

class ClaimQueuedMessageMapperTest {

    private final ClaimQueuedMessageMapper mapper = new ClaimQueuedMessageMapper();

    @Test
    void should_map_a_claim_to_a_queued_message() {
        // given
        Claim claim = Claim.accept(
                ClaimType.FUNERAL,
                "C-1",
                "P-1",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 1),
                null,
                null,
                Clock.fixed(Instant.parse("2026-03-01T10:15:30Z"), ZoneOffset.UTC));

        // when
        ClaimQueuedMessage message = mapper.map(claim);

        // then
        assertThat(message).isEqualTo(new ClaimQueuedMessage(claim.id(), ClaimType.FUNERAL));
    }

    @Test
    void should_reject_a_null_claim() {
        // given / when / then
        assertThatThrownBy(() -> mapper.map(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claim");
    }
}
