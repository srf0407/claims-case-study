package com.claimsprocessing.claims.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimType;
import org.junit.jupiter.api.Test;

class InMemoryClaimRepositoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    private final InMemoryClaimRepository repository = new InMemoryClaimRepository();

    @Test
    void should_return_the_saved_claim_for_its_id() {
        // given
        Claim claim = acceptedClaim();

        // when
        repository.save(claim);

        // then
        var found = repository.findById(claim.id());
        assertThat(found).isPresent();
        assertThat(found.orElseThrow()).isSameAs(claim);
        assertThat(found.orElseThrow().id()).isEqualTo(claim.id());
        assertThat(found.orElseThrow().status()).isEqualTo(ClaimStatus.ACCEPTED);
    }

    @Test
    void should_return_empty_for_an_unknown_id() {
        // given
        var id = new ClaimId(UUID.randomUUID());

        // when
        var found = repository.findById(id);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void should_replace_the_claim_when_the_same_id_is_saved_again() {
        // given
        Claim accepted = acceptedClaim();
        Claim awaitingAnalyst = accepted.transitionTo(ClaimStatus.AWAITING_ANALYST, CLOCK);
        repository.save(accepted);

        // when
        repository.save(awaitingAnalyst);

        // then
        var found = repository.findById(accepted.id());
        assertThat(found).isPresent();
        assertThat(found.orElseThrow()).isSameAs(awaitingAnalyst);
        assertThat(found.orElseThrow().status()).isEqualTo(ClaimStatus.AWAITING_ANALYST);
    }

    @Test
    void should_reject_a_null_claim() {
        // when / then
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void should_return_empty_or_throw_for_a_null_id() {
        // given
        repository.save(acceptedClaim());

        // when
        var found = repository.findById(null);

        // then
        assertThat(found).isEmpty();
    }

    private static Claim acceptedClaim() {
        return Claim.accept(
                ClaimType.FUNERAL,
                "C-1",
                "P-1",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 1, 15),
                null,
                null,
                CLOCK);
    }
}
