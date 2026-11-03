package com.claimsprocessing.claims.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.claimsprocessing.claims.application.port.out.PaymentRequest;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimType;
import org.junit.jupiter.api.Test;

class PaymentRequestMapperTest {

    @Test
    void should_map_a_claim_to_a_payment_request() {
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
        PaymentRequest request = new PaymentRequestMapper().map(claim);

        // then
        assertThat(request).isEqualTo(new PaymentRequest(
                claim.id().value().toString(),
                claim.id(),
                "C-1",
                "P-1",
                new BigDecimal("150.00")));
    }
}
