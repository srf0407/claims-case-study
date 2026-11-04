package com.claimsprocessing.claims.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.domain.RejectionReason;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class ClaimItemMapperTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    private static final Clock LATER = Clock.fixed(Instant.parse("2026-01-15T11:00:00Z"), ZoneOffset.UTC);

    private final ClaimItemMapper mapper = new ClaimItemMapper();

    @Test
    void should_map_a_funeral_claim_and_omit_empty_fields() {
        // given
        Claim claim = funeral();

        // when
        Map<String, AttributeValue> item = mapper.toAttributes(claim);

        // then
        assertThat(item.keySet())
                .containsExactlyInAnyOrder(
                        "id", "clientId", "policyNumber", "claimType", "amount", "incidentDate", "status", "events");
        assertThat(item.get("amount").s()).isEqualTo("10.50");
        assertThat(item.get("claimType").s()).isEqualTo("FUNERAL");
        assertThat(item.get("status").s()).isEqualTo("ACCEPTED");
        Map<String, AttributeValue> event = item.get("events").l().getFirst().m();
        assertThat(event.keySet()).containsExactlyInAnyOrder("type", "occurredAt");
        assertThat(event.get("type").s()).isEqualTo("ACCEPTED");
        assertThat(event.get("occurredAt").s()).isEqualTo("2026-01-15T10:00:00Z");
        assertThat(mapper.toClaim(item)).usingRecursiveComparison().isEqualTo(claim);
    }

    @Test
    void should_map_a_rejected_death_claim_and_its_events() {
        // given
        Claim claim = rejectedDeath();

        // when
        Map<String, AttributeValue> item = mapper.toAttributes(claim);

        // then
        assertThat(item.get("claimType").s()).isEqualTo("DEATH");
        assertThat(item.get("dateOfDeath").s()).isEqualTo("2026-01-10");
        assertThat(item.get("deathCertificateRef").s()).isEqualTo("DC-1");
        assertThat(item.get("status").s()).isEqualTo("REJECTED");
        assertThat(item.get("rejectionReason").s()).isEqualTo("UNKNOWN_CLIENT");
        assertThat(item.get("events").l()).hasSize(2);
        Map<String, AttributeValue> accepted = item.get("events").l().get(0).m();
        assertThat(accepted.keySet()).containsExactlyInAnyOrder("type", "occurredAt");
        assertThat(accepted.get("type").s()).isEqualTo("ACCEPTED");
        Map<String, AttributeValue> changed = item.get("events").l().get(1).m();
        assertThat(changed.keySet()).containsExactlyInAnyOrder("type", "occurredAt", "from", "to", "reason");
        assertThat(changed.get("type").s()).isEqualTo("STATUS_CHANGED");
        assertThat(changed.get("from").s()).isEqualTo("ACCEPTED");
        assertThat(changed.get("to").s()).isEqualTo("REJECTED");
        assertThat(changed.get("reason").s()).isEqualTo("UNKNOWN_CLIENT");
        assertThat(mapper.toClaim(item)).usingRecursiveComparison().isEqualTo(claim);
    }

    @Test
    void should_omit_the_reason_when_the_status_change_has_none() {
        // given
        Claim claim = funeral().transitionTo(ClaimStatus.AWAITING_ANALYST, LATER);

        // when
        Map<String, AttributeValue> changed = mapper.toAttributes(claim).get("events").l().get(1).m();

        // then
        assertThat(changed.keySet()).containsExactlyInAnyOrder("type", "occurredAt", "from", "to");
        assertThat(changed.get("type").s()).isEqualTo("STATUS_CHANGED");
        assertThat(changed.get("from").s()).isEqualTo("ACCEPTED");
        assertThat(changed.get("to").s()).isEqualTo("AWAITING_ANALYST");
        assertThat(changed).doesNotContainKey("reason");
    }

    @Test
    void should_keep_the_amount_scale() {
        // given
        Claim claim = funeral();

        // when
        Claim restored = mapper.toClaim(mapper.toAttributes(claim));

        // then
        assertThat(restored.amount()).isEqualTo(new BigDecimal("10.50"));
    }

    private static Claim funeral() {
        return Claim.accept(
                ClaimType.FUNERAL,
                "client-1",
                "POL-1",
                new BigDecimal("10.50"),
                LocalDate.of(2026, 1, 15),
                null,
                null,
                CLOCK);
    }

    private static Claim rejectedDeath() {
        Claim accepted = Claim.accept(
                ClaimType.DEATH,
                "client-1",
                "POL-1",
                new BigDecimal("250.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 10),
                "DC-1",
                CLOCK);
        return accepted.reject(RejectionReason.UNKNOWN_CLIENT, LATER);
    }
}
