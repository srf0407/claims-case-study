package com.claimsprocessing.claims.domain;

import static com.claimsprocessing.claims.domain.ClaimBuilder.aClaim;
import static com.claimsprocessing.claims.domain.ClaimStatus.ACCEPTED;
import static com.claimsprocessing.claims.domain.ClaimStatus.APPROVED;
import static com.claimsprocessing.claims.domain.ClaimStatus.AWAITING_ANALYST;
import static com.claimsprocessing.claims.domain.ClaimStatus.PAID;
import static com.claimsprocessing.claims.domain.ClaimStatus.PAYMENT_FAILED;
import static com.claimsprocessing.claims.domain.ClaimStatus.PAYMENT_PENDING;
import static com.claimsprocessing.claims.domain.ClaimStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ClaimTest {

    private static final Set<List<ClaimStatus>> LEGAL_PAIRS = Set.of(
            List.of(ACCEPTED, AWAITING_ANALYST),
            List.of(ACCEPTED, PAYMENT_PENDING),
            List.of(AWAITING_ANALYST, APPROVED),
            List.of(APPROVED, PAYMENT_PENDING),
            List.of(PAYMENT_PENDING, PAID),
            List.of(PAYMENT_PENDING, PAYMENT_FAILED));

    @Test
    void should_accept_a_death_claim_in_accepted() {
        // given
        var amount = new BigDecimal("250.00");
        var incidentDate = LocalDate.of(2026, 3, 1);
        var dateOfDeath = LocalDate.of(2026, 2, 20);

        // when
        Claim claim = aClaim()
                .death()
                .clientId("C-9")
                .policyNumber("P-9")
                .amount(amount)
                .incidentDate(incidentDate)
                .dateOfDeath(dateOfDeath)
                .deathCertificateRef("CERT-9")
                .build();

        // then
        assertThat(claim.status()).isEqualTo(ACCEPTED);
        assertThat(claim.id()).isNotNull();
        assertThat(claim.id().value()).isNotNull();
        assertThat(claim.claimType()).isEqualTo(ClaimType.DEATH);
        assertThat(claim.clientId()).isEqualTo("C-9");
        assertThat(claim.policyNumber()).isEqualTo("P-9");
        assertThat(claim.amount()).isEqualTo(amount);
        assertThat(claim.incidentDate()).isEqualTo(incidentDate);
        assertThat(claim.dateOfDeath()).isEqualTo(dateOfDeath);
        assertThat(claim.deathCertificateRef()).isEqualTo("CERT-9");
    }

    @ParameterizedTest
    @EnumSource(value = ClaimType.class, names = {"DISABILITY", "FUNERAL"})
    void should_accept_a_non_death_claim_without_death_fields(ClaimType claimType) {
        // given / when
        Claim claim = aClaim().claimType(claimType).build();

        // then
        assertThat(claim.status()).isEqualTo(ACCEPTED);
        assertThat(claim.claimType()).isEqualTo(claimType);
        assertThat(claim.dateOfDeath()).isNull();
        assertThat(claim.deathCertificateRef()).isNull();
    }

    @Test
    void should_assign_a_different_id_on_each_accept() {
        // given / when
        Claim first = aClaim().build();
        Claim second = aClaim().build();

        // then
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void should_record_one_claim_accepted_event_when_the_claim_is_accepted() {
        // given
        Instant occurredAt = Instant.parse("2026-04-01T08:30:00Z");
        Clock clock = clockAt(occurredAt);

        // when
        Claim claim = aClaim().clock(clock).build();

        // then
        assertThat(claim.events()).containsExactly(new ClaimAccepted(occurredAt));
    }

    @ParameterizedTest(name = "{0} to {1}")
    @MethodSource("legalPairs")
    void should_move_through_each_legal_status_pair(ClaimStatus from, ClaimStatus to) {
        // given
        Clock clock = clockAt(Instant.parse("2026-04-01T08:00:00Z"));
        Claim source = claimIn(from, clock);

        // when
        Claim moved = source.transitionTo(to, clock);

        // then
        assertThat(moved.status()).isEqualTo(to);
        assertThat(moved.id()).isEqualTo(source.id());
    }

    @ParameterizedTest(name = "{0} to {1}")
    @MethodSource("legalPairs")
    void should_append_exactly_one_status_changed_event_for_each_legal_pair(ClaimStatus from, ClaimStatus to) {
        // given
        Clock setup = clockAt(Instant.parse("2026-04-01T08:00:00Z"));
        Clock transition = clockAt(Instant.parse("2026-04-01T09:00:00Z"));
        Claim source = claimIn(from, setup);
        var expected = new ArrayList<ClaimEvent>(source.events());
        expected.add(new ClaimStatusChanged(from, to, Optional.empty(), transition.instant()));

        // when
        Claim moved = source.transitionTo(to, transition);

        // then
        assertThat(moved.events()).isEqualTo(expected);
    }

    @Test
    void should_leave_the_original_claim_unchanged_after_a_legal_transition() {
        // given
        Claim original = aClaim().build();
        List<ClaimEvent> eventsBefore = List.copyOf(original.events());

        // when
        original.transitionTo(AWAITING_ANALYST, clockAt(Instant.parse("2026-04-01T09:00:00Z")));

        // then
        assertThat(original.status()).isEqualTo(ACCEPTED);
        assertThat(original.events()).isEqualTo(eventsBefore);
    }

    @ParameterizedTest(name = "{0} to {1}")
    @MethodSource("illegalPairs")
    void should_reject_each_illegal_status_pair(ClaimStatus from, ClaimStatus to) {
        // given
        Clock clock = clockAt(Instant.parse("2026-04-01T08:00:00Z"));
        Claim source = claimIn(from, clock);

        // when / then
        assertThatThrownBy(() -> source.transitionTo(to, clock))
                .isInstanceOf(IllegalClaimTransitionException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @ParameterizedTest(name = "{0} to {1}")
    @MethodSource("illegalPairs")
    void should_add_no_event_when_the_transition_is_illegal(ClaimStatus from, ClaimStatus to) {
        // given
        Clock clock = clockAt(Instant.parse("2026-04-01T08:00:00Z"));
        Claim source = claimIn(from, clock);
        List<ClaimEvent> eventsBefore = List.copyOf(source.events());

        // when
        assertThatThrownBy(() -> source.transitionTo(to, clock))
                .isInstanceOf(IllegalClaimTransitionException.class);

        // then
        assertThat(source.events()).isEqualTo(eventsBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01"})
    void should_reject_an_amount_that_is_not_above_zero(String rawAmount) {
        // given / when / then
        assertThatThrownBy(() -> aClaim().amount(new BigDecimal(rawAmount)).build())
                .isInstanceOf(InvalidClaimException.class);
    }

    @Test
    void should_keep_the_submitted_amount_scale() {
        // given
        var amount = new BigDecimal("10.50");

        // when
        Claim claim = aClaim().amount(amount).build();

        // then
        assertThat(claim.amount()).isEqualTo(amount);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void should_reject_a_blank_or_missing_client_id(String clientId) {
        // given / when / then
        assertThatThrownBy(() -> aClaim().clientId(clientId).build())
                .isInstanceOf(InvalidClaimException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void should_reject_a_blank_or_missing_policy_number(String policyNumber) {
        // given / when / then
        assertThatThrownBy(() -> aClaim().policyNumber(policyNumber).build())
                .isInstanceOf(InvalidClaimException.class);
    }

    @ParameterizedTest(name = "dateOfDeath={0}, deathCertificateRef={1}")
    @MethodSource("missingDeathDetails")
    void should_reject_a_death_claim_without_death_details(LocalDate dateOfDeath, String deathCertificateRef) {
        // given / when / then
        assertThatThrownBy(() -> aClaim()
                        .death()
                        .dateOfDeath(dateOfDeath)
                        .deathCertificateRef(deathCertificateRef)
                        .build())
                .isInstanceOf(InvalidClaimException.class);
    }

    @ParameterizedTest(name = "{0} dateOfDeath={1}, deathCertificateRef={2}")
    @MethodSource("nonDeathClaimsWithDeathFields")
    void should_reject_a_non_death_claim_that_has_death_fields(
            ClaimType claimType, LocalDate dateOfDeath, String deathCertificateRef) {
        // given / when / then
        assertThatThrownBy(() -> aClaim()
                        .claimType(claimType)
                        .dateOfDeath(dateOfDeath)
                        .deathCertificateRef(deathCertificateRef)
                        .build())
                .isInstanceOf(InvalidClaimException.class);
    }

    @ParameterizedTest
    @MethodSource("missingRequiredValues")
    void should_reject_a_claim_missing_a_required_value(ClaimBuilder builder) {
        // given / when / then
        assertThatThrownBy(builder::build).isInstanceOf(InvalidClaimException.class);
    }

    @Test
    void should_reject_a_null_claim_id() {
        // given / when / then
        assertThatThrownBy(() -> new ClaimId(null)).isInstanceOf(InvalidClaimException.class);
    }

    @Test
    void should_reject_a_null_clock() {
        // given
        Claim claim = aClaim().build();

        // when / then
        assertThatThrownBy(() -> Claim.accept(
                        ClaimType.FUNERAL,
                        "client-1",
                        "POL-1",
                        new BigDecimal("10.00"),
                        LocalDate.of(2026, 1, 15),
                        null,
                        null,
                        null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock");
        assertThatThrownBy(() -> claim.transitionTo(REJECTED, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock");
        assertThatThrownBy(() -> claim.reject(RejectionReason.UNKNOWN_CLIENT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock");
    }

    @ParameterizedTest
    @EnumSource(value = RejectionReason.class, names = {"UNKNOWN_CLIENT", "POLICY_LAPSED", "NO_COVER"})
    void should_reject_an_accepted_claim_with_a_business_reason(RejectionReason reason) {
        // given
        Instant acceptedAt = Instant.parse("2026-04-01T08:00:00Z");
        Instant rejectedAt = Instant.parse("2026-04-01T09:00:00Z");
        Claim source = aClaim().clock(clockAt(acceptedAt)).build();
        var expected = new ArrayList<ClaimEvent>(source.events());
        expected.add(new ClaimStatusChanged(ACCEPTED, REJECTED, Optional.of(reason), rejectedAt));

        // when
        Claim rejected = source.reject(reason, clockAt(rejectedAt));

        // then
        assertThat(rejected.status()).isEqualTo(REJECTED);
        assertThat(rejected.rejectionReason()).contains(reason);
        assertThat(rejected.id()).isEqualTo(source.id());
        assertThat(rejected.events()).isEqualTo(expected);
    }

    @Test
    void should_reject_an_awaiting_analyst_claim_as_analyst_rejected() {
        // given
        Instant acceptedAt = Instant.parse("2026-04-01T08:00:00Z");
        Instant rejectedAt = Instant.parse("2026-04-01T09:00:00Z");
        Claim source = claimIn(AWAITING_ANALYST, clockAt(acceptedAt));
        var expected = new ArrayList<ClaimEvent>(source.events());
        expected.add(new ClaimStatusChanged(
                AWAITING_ANALYST, REJECTED, Optional.of(RejectionReason.ANALYST_REJECTED), rejectedAt));

        // when
        Claim rejected = source.reject(RejectionReason.ANALYST_REJECTED, clockAt(rejectedAt));

        // then
        assertThat(rejected.status()).isEqualTo(REJECTED);
        assertThat(rejected.rejectionReason()).contains(RejectionReason.ANALYST_REJECTED);
        assertThat(rejected.id()).isEqualTo(source.id());
        assertThat(rejected.events()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("mismatchedRejectionReasons")
    void should_refuse_a_rejection_reason_that_does_not_match_the_source_status(
            ClaimStatus status, RejectionReason reason) {
        // given
        Clock clock = clockAt(Instant.parse("2026-04-01T08:00:00Z"));
        Claim source = claimIn(status, clock);
        List<ClaimEvent> eventsBefore = List.copyOf(source.events());
        Optional<RejectionReason> reasonBefore = source.rejectionReason();

        // when / then
        assertThatThrownBy(() -> source.reject(reason, clock))
                .isInstanceOf(IllegalClaimTransitionException.class);
        assertThat(source.status()).isEqualTo(status);
        assertThat(source.rejectionReason()).isEqualTo(reasonBefore);
        assertThat(source.events()).isEqualTo(eventsBefore);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, mode = Mode.EXCLUDE, names = {"ACCEPTED", "AWAITING_ANALYST"})
    void should_refuse_reject_when_no_arrow_leads_to_rejected(ClaimStatus status) {
        // given
        Clock clock = clockAt(Instant.parse("2026-04-01T08:00:00Z"));
        Claim source = claimIn(status, clock);
        List<ClaimEvent> eventsBefore = List.copyOf(source.events());
        Optional<RejectionReason> reasonBefore = source.rejectionReason();

        // when / then
        assertThatThrownBy(() -> source.reject(RejectionReason.UNKNOWN_CLIENT, clock))
                .isInstanceOf(IllegalClaimTransitionException.class);
        assertThat(source.status()).isEqualTo(status);
        assertThat(source.rejectionReason()).isEqualTo(reasonBefore);
        assertThat(source.events()).isEqualTo(eventsBefore);
    }

    @Test
    void should_leave_rejection_reason_empty_until_the_claim_is_rejected() {
        // given / when
        Claim claim = aClaim().build();

        // then
        assertThat(claim.rejectionReason()).isEmpty();
    }

    @Test
    void should_leave_the_original_claim_unchanged_after_reject() {
        // given
        Claim original = aClaim().build();
        List<ClaimEvent> eventsBefore = List.copyOf(original.events());

        // when
        original.reject(RejectionReason.UNKNOWN_CLIENT, clockAt(Instant.parse("2026-04-01T09:00:00Z")));

        // then
        assertThat(original.status()).isEqualTo(ACCEPTED);
        assertThat(original.rejectionReason()).isEmpty();
        assertThat(original.events()).isEqualTo(eventsBefore);
    }

    @Test
    void should_reject_a_null_reason() {
        // given
        Claim claim = aClaim().build();
        List<ClaimEvent> eventsBefore = List.copyOf(claim.events());

        // when / then
        assertThatThrownBy(() -> claim.reject(null, clockAt(Instant.parse("2026-04-01T09:00:00Z"))))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("reason");
        assertThat(claim.status()).isEqualTo(ACCEPTED);
        assertThat(claim.rejectionReason()).isEmpty();
        assertThat(claim.events()).isEqualTo(eventsBefore);
    }

    private static Stream<Arguments> legalPairs() {
        return LEGAL_PAIRS.stream().map(pair -> Arguments.of(pair.get(0), pair.get(1)));
    }

    private static Stream<Arguments> illegalPairs() {
        return Arrays.stream(ClaimStatus.values())
                .flatMap(from -> Arrays.stream(ClaimStatus.values())
                        .filter(to -> !LEGAL_PAIRS.contains(List.of(from, to)))
                        .map(to -> Arguments.of(from, to)));
    }

    private static Stream<Arguments> mismatchedRejectionReasons() {
        return Stream.of(
                Arguments.of(ACCEPTED, RejectionReason.ANALYST_REJECTED),
                Arguments.of(AWAITING_ANALYST, RejectionReason.UNKNOWN_CLIENT),
                Arguments.of(AWAITING_ANALYST, RejectionReason.POLICY_LAPSED),
                Arguments.of(AWAITING_ANALYST, RejectionReason.NO_COVER));
    }

    private static Stream<Arguments> missingDeathDetails() {
        var dateOfDeath = LocalDate.of(2026, 2, 20);
        return Stream.of(
                Arguments.of(null, "DC-1"),
                Arguments.of(dateOfDeath, null),
                Arguments.of(dateOfDeath, ""),
                Arguments.of(dateOfDeath, " "));
    }

    private static Stream<Arguments> nonDeathClaimsWithDeathFields() {
        var dateOfDeath = LocalDate.of(2026, 2, 20);
        return Stream.of(ClaimType.DISABILITY, ClaimType.FUNERAL)
                .flatMap(claimType -> Stream.of(
                        Arguments.of(claimType, dateOfDeath, null),
                        Arguments.of(claimType, null, "CERT"),
                        Arguments.of(claimType, null, " "),
                        Arguments.of(claimType, dateOfDeath, "CERT")));
    }

    private static Stream<ClaimBuilder> missingRequiredValues() {
        return Stream.of(
                aClaim().claimType(null),
                aClaim().amount(null),
                aClaim().incidentDate(null));
    }

    private static Claim claimIn(ClaimStatus status, Clock clock) {
        Claim accepted = aClaim().clock(clock).build();
        return switch (status) {
            case ACCEPTED -> accepted;
            case REJECTED -> accepted.reject(RejectionReason.UNKNOWN_CLIENT, clock);
            case AWAITING_ANALYST -> accepted.transitionTo(AWAITING_ANALYST, clock);
            case APPROVED -> accepted.transitionTo(AWAITING_ANALYST, clock).transitionTo(APPROVED, clock);
            case PAYMENT_PENDING -> accepted.transitionTo(PAYMENT_PENDING, clock);
            case PAID -> accepted.transitionTo(PAYMENT_PENDING, clock).transitionTo(PAID, clock);
            case PAYMENT_FAILED -> accepted.transitionTo(PAYMENT_PENDING, clock).transitionTo(PAYMENT_FAILED, clock);
        };
    }

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
