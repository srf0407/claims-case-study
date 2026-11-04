package com.claimsprocessing.claims.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.domain.RejectionReason;
import org.junit.jupiter.api.Test;

class GetClaimMapperTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-01-15T11:00:00Z");

    private final GetClaimMapper mapper = new GetClaimMapper();

    @Test
    void should_map_all_claim_fields_and_leave_nulls_null() {
        // given
        Claim claim = acceptedClaim();

        // when
        GetClaimResponse response = mapper.toResponse(claim);

        // then
        assertThat(response.claimId()).isEqualTo(claim.id().value());
        assertThat(response.clientId()).isEqualTo("C-1");
        assertThat(response.policyNumber()).isEqualTo("P-1");
        assertThat(response.claimType()).isEqualTo(ClaimType.FUNERAL);
        assertThat(response.amount()).isEqualTo(new BigDecimal("150.00"));
        assertThat(response.incidentDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(response.dateOfDeath()).isNull();
        assertThat(response.deathCertificateRef()).isNull();
        assertThat(response.status()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(response.rejectionReason()).isNull();
    }

    @Test
    void should_map_an_accepted_event() {
        // given
        Claim claim = acceptedClaim();

        // when
        GetClaimResponse response = mapper.toResponse(claim);

        // then
        assertThat(response.events()).containsExactly(
                new EventResponse("ACCEPTED", ACCEPTED_AT, null, null, null));
    }

    @Test
    void should_map_a_status_change_with_a_reason() {
        // given
        Claim rejected = acceptedClaim().reject(
                RejectionReason.UNKNOWN_CLIENT, Clock.fixed(CHANGED_AT, ZoneOffset.UTC));

        // when
        EventResponse change = mapper.toResponse(rejected).events().get(1);

        // then
        assertThat(change).isEqualTo(new EventResponse(
                "STATUS_CHANGED",
                CHANGED_AT,
                ClaimStatus.ACCEPTED,
                ClaimStatus.REJECTED,
                RejectionReason.UNKNOWN_CLIENT));
    }

    @Test
    void should_map_a_status_change_without_a_reason() {
        // given
        Claim awaitingAnalyst = acceptedClaim().transitionTo(
                ClaimStatus.AWAITING_ANALYST, Clock.fixed(CHANGED_AT, ZoneOffset.UTC));

        // when
        EventResponse change = mapper.toResponse(awaitingAnalyst).events().get(1);

        // then
        assertThat(change.type()).isEqualTo("STATUS_CHANGED");
        assertThat(change.from()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(change.to()).isEqualTo(ClaimStatus.AWAITING_ANALYST);
        assertThat(change.reason()).isNull();
    }

    @Test
    void should_keep_events_in_claim_order() {
        // given
        Claim awaitingAnalyst = acceptedClaim().transitionTo(
                ClaimStatus.AWAITING_ANALYST, Clock.fixed(CHANGED_AT, ZoneOffset.UTC));

        // when
        GetClaimResponse response = mapper.toResponse(awaitingAnalyst);

        // then
        assertThat(response.events()).extracting(EventResponse::type)
                .containsExactly("ACCEPTED", "STATUS_CHANGED");
    }

    @Test
    void should_map_the_rejection_reason_of_a_rejected_claim() {
        // given
        Claim rejected = acceptedClaim().reject(
                RejectionReason.UNKNOWN_CLIENT, Clock.fixed(CHANGED_AT, ZoneOffset.UTC));

        // when
        GetClaimResponse response = mapper.toResponse(rejected);

        // then
        assertThat(response.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo(RejectionReason.UNKNOWN_CLIENT);
    }

    @Test
    void should_reject_a_null_claim_to_the_mapper() {
        // given / when / then
        assertThatThrownBy(() -> mapper.toResponse(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claim");
    }

    @Test
    void should_reject_a_null_id_to_the_mapper() {
        // given / when / then
        assertThatThrownBy(() -> mapper.toQuery(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id");
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
