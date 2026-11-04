package com.claimsprocessing.claims.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.in.GetClaimQuery;
import com.claimsprocessing.claims.application.port.in.GetClaimUseCase;
import com.claimsprocessing.claims.application.port.out.InMemoryClaimRepository;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimType;
import org.junit.jupiter.api.Test;

class GetClaimServiceTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-01-15T10:00:00Z");

    private final InMemoryClaimRepository claims = new InMemoryClaimRepository();
    private final GetClaimUseCase getClaim = new GetClaimService(claims);

    @Test
    void should_return_the_stored_claim() {
        // given
        Claim claim = acceptedClaim();
        claims.save(claim);

        // when
        Claim found = getClaim.get(new GetClaimQuery(claim.id()));

        // then
        assertThat(found).isSameAs(claim);
        assertThat(found.status()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(claims.findById(claim.id()).orElseThrow().status()).isEqualTo(ClaimStatus.ACCEPTED);
    }

    @Test
    void should_fail_when_the_claim_is_missing() {
        // given
        ClaimId id = new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        // when / then
        assertThatThrownBy(() -> getClaim.get(new GetClaimQuery(id)))
                .isInstanceOf(ClaimNotFoundException.class)
                .hasMessageContaining(id.value().toString());
        assertThat(claims.isEmpty()).isTrue();
    }

    @Test
    void should_reject_a_null_query() {
        // given / when / then
        assertThatThrownBy(() -> getClaim.get(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("query");
        assertThat(claims.isEmpty()).isTrue();
    }

    @Test
    void should_reject_a_null_claim_id() {
        // given / when / then
        assertThatThrownBy(() -> getClaim.get(new GetClaimQuery(null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimId");
        assertThat(claims.isEmpty()).isTrue();
    }

    private static Claim acceptedClaim() {
        return Claim.accept(
                ClaimType.FUNERAL,
                "C-1",
                "P-1",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 1),
                null,
                null,
                Clock.fixed(ACCEPTED_AT, ZoneOffset.UTC));
    }
}
