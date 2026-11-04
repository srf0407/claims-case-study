package com.claimsprocessing.claims.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.in.SubmitClaimCommand;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;
import org.junit.jupiter.api.Test;

class SubmitClaimMapperTest {

    private final SubmitClaimMapper mapper = new SubmitClaimMapper();

    @Test
    void should_map_a_request_to_a_command() {
        // given
        var request = new SubmitClaimRequest(
                ClaimType.FUNERAL,
                "C-1",
                "P-1",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 1),
                null,
                null);

        // when
        SubmitClaimCommand command = mapper.toCommand(request);

        // then
        assertThat(command).isEqualTo(new SubmitClaimCommand(
                ClaimType.FUNERAL,
                "C-1",
                "P-1",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 3, 1),
                null,
                null));
    }

    @Test
    void should_pass_death_fields_through_to_the_command() {
        // given
        var dateOfDeath = LocalDate.of(2026, 2, 20);
        var request = new SubmitClaimRequest(
                ClaimType.DEATH,
                "C-9",
                "P-9",
                new BigDecimal("250.00"),
                LocalDate.of(2026, 3, 1),
                dateOfDeath,
                "CERT-9");

        // when
        SubmitClaimCommand command = mapper.toCommand(request);

        // then
        assertThat(command.claimType()).isEqualTo(ClaimType.DEATH);
        assertThat(command.dateOfDeath()).isEqualTo(dateOfDeath);
        assertThat(command.deathCertificateRef()).isEqualTo("CERT-9");
    }

    @Test
    void should_map_a_claim_id_to_the_response() {
        // given
        var claimId = new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        // when
        SubmitClaimResponse response = mapper.toResponse(claimId);

        // then
        assertThat(response).isEqualTo(new SubmitClaimResponse(claimId.value()));
    }

    @Test
    void should_reject_a_null_request_to_the_mapper() {
        // given / when / then
        assertThatThrownBy(() -> mapper.toCommand(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request");
    }
}
