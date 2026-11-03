package com.claimsprocessing.claims.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.claimsprocessing.claims.application.port.in.SubmitClaimCommand;
import com.claimsprocessing.claims.application.port.in.SubmitClaimUseCase;
import com.claimsprocessing.claims.application.port.out.InMemoryClaimRepository;
import com.claimsprocessing.claims.domain.ClaimAccepted;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.domain.InvalidClaimException;
import org.junit.jupiter.api.Test;

class SubmitClaimServiceTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-03-01T10:15:30Z");

    private final Clock clock = Clock.fixed(ACCEPTED_AT, ZoneOffset.UTC);
    private final InMemoryClaimRepository claims = new InMemoryClaimRepository();
    private final SubmitClaimUseCase submitClaim = new SubmitClaimService(claims, clock);

    @Test
    void should_store_an_accepted_claim_and_return_its_id() {
        // given
        var command = funeralCommand();

        // when
        ClaimId id = submitClaim.submit(command);

        // then
        var stored = claims.findById(id).orElseThrow();
        assertThat(id).isEqualTo(stored.id());
        assertThat(stored.status()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(stored.claimType()).isEqualTo(ClaimType.FUNERAL);
        assertThat(stored.clientId()).isEqualTo("C-1");
        assertThat(stored.policyNumber()).isEqualTo("P-1");
        assertThat(stored.amount()).isEqualTo(new BigDecimal("150.00"));
        assertThat(stored.incidentDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(stored.dateOfDeath()).isNull();
        assertThat(stored.deathCertificateRef()).isNull();
        assertThat(stored.events()).containsExactly(new ClaimAccepted(ACCEPTED_AT));
    }

    @Test
    void should_store_death_fields_on_a_death_claim() {
        // given
        var dateOfDeath = LocalDate.of(2026, 2, 20);
        var command = new SubmitClaimCommand(
                ClaimType.DEATH,
                "C-9",
                "P-9",
                new BigDecimal("250.00"),
                LocalDate.of(2026, 3, 1),
                dateOfDeath,
                "CERT-9");

        // when
        ClaimId id = submitClaim.submit(command);

        // then
        var stored = claims.findById(id).orElseThrow();
        assertThat(stored.status()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(stored.claimType()).isEqualTo(ClaimType.DEATH);
        assertThat(stored.dateOfDeath()).isEqualTo(dateOfDeath);
        assertThat(stored.deathCertificateRef()).isEqualTo("CERT-9");
    }

    @Test
    void should_store_a_second_claim_when_submit_is_repeated() {
        // given
        var command = funeralCommand();

        // when
        ClaimId first = submitClaim.submit(command);
        ClaimId second = submitClaim.submit(command);

        // then
        assertThat(first).isNotEqualTo(second);
        assertThat(claims.findById(first)).isPresent();
        assertThat(claims.findById(second)).isPresent();
    }

    @Test
    void should_save_nothing_when_the_claim_is_rejected() {
        // given
        var command = new SubmitClaimCommand(
                ClaimType.FUNERAL,
                " ",
                "P-1",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 1),
                null,
                null);

        // when / then
        assertThatThrownBy(() -> submitClaim.submit(command))
                .isInstanceOf(InvalidClaimException.class);
        assertThat(claims.isEmpty()).isTrue();
    }

    @Test
    void should_reject_a_null_command() {
        // given / when / then
        assertThatThrownBy(() -> submitClaim.submit(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command");
        assertThat(claims.isEmpty()).isTrue();
    }

    private static SubmitClaimCommand funeralCommand() {
        return new SubmitClaimCommand(
                ClaimType.FUNERAL,
                "C-1",
                "P-1",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 1),
                null,
                null);
    }
}
