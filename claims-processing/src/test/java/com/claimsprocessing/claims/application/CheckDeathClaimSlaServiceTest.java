package com.claimsprocessing.claims.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.DeathClaimSlaAlarm;
import com.claimsprocessing.claims.application.port.out.DeathClaimSlaAlarmPort;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.domain.DeathSlaLevel;
import org.junit.jupiter.api.Test;

class CheckDeathClaimSlaServiceTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant JUST_BEFORE_WARNING = Instant.parse("2026-01-15T12:59:59Z");
    private static final Instant WARNING_AT = Instant.parse("2026-01-15T13:00:00Z");
    private static final Instant BREACH_AT = Instant.parse("2026-01-15T14:00:00Z");
    private static final Duration SLA = Duration.ofHours(4);
    private static final double WARNING_FRACTION = 0.75;

    private final MutableClock clock = new MutableClock(ACCEPTED_AT);
    private final FakeClaimRepository claims = new FakeClaimRepository();
    private final RecordingAlarmPort alarms = new RecordingAlarmPort();
    private final RaisedDeathClaimSlaAlarms raisedAlarms = new RaisedDeathClaimSlaAlarms();
    private final CheckDeathClaimSlaService service = new CheckDeathClaimSlaService(
            claims, alarms, raisedAlarms, clock, SLA, WARNING_FRACTION);

    @Test
    void should_raise_nothing_when_there_are_no_open_claims() {
        // given
        clock.set(BREACH_AT);

        // when
        service.check();

        // then
        assertThat(alarms.raised()).isEmpty();
    }

    @Test
    void should_raise_nothing_when_the_open_death_claim_is_still_on_time() {
        // given
        claims.open(deathClaim());
        clock.set(JUST_BEFORE_WARNING);

        // when
        service.check();

        // then
        assertThat(alarms.raised()).isEmpty();
    }

    @Test
    void should_raise_one_warning_when_the_claim_reaches_the_warning() {
        // given
        Claim claim = deathClaim();
        claims.open(claim);
        clock.set(WARNING_AT);

        // when
        service.check();

        // then
        assertThat(alarms.raised()).containsExactly(
                new DeathClaimSlaAlarm(claim.id(), DeathSlaLevel.WARNING, Duration.ofHours(3)));
    }

    @Test
    void should_not_raise_the_warning_again_on_the_next_check() {
        // given
        Claim claim = deathClaim();
        claims.open(claim);
        clock.set(WARNING_AT);
        service.check();

        // when
        service.check();

        // then
        assertThat(alarms.raised()).containsExactly(
                new DeathClaimSlaAlarm(claim.id(), DeathSlaLevel.WARNING, Duration.ofHours(3)));
    }

    @Test
    void should_raise_only_a_breach_when_the_first_check_is_past_both_thresholds() {
        // given
        Claim claim = deathClaim();
        claims.open(claim);
        clock.set(BREACH_AT);

        // when
        service.check();

        // then
        assertThat(alarms.raised()).containsExactly(
                new DeathClaimSlaAlarm(claim.id(), DeathSlaLevel.BREACH, Duration.ofHours(4)));
    }

    @Test
    void should_raise_a_breach_after_an_earlier_warning() {
        // given
        Claim claim = deathClaim();
        claims.open(claim);
        clock.set(WARNING_AT);
        service.check();
        clock.set(BREACH_AT);

        // when
        service.check();

        // then
        assertThat(alarms.raised()).containsExactly(
                new DeathClaimSlaAlarm(claim.id(), DeathSlaLevel.WARNING, Duration.ofHours(3)),
                new DeathClaimSlaAlarm(claim.id(), DeathSlaLevel.BREACH, Duration.ofHours(4)));
    }

    @Test
    void should_not_raise_the_breach_again_on_the_next_check() {
        // given
        Claim claim = deathClaim();
        claims.open(claim);
        clock.set(BREACH_AT);
        service.check();

        // when
        service.check();

        // then
        assertThat(alarms.raised()).containsExactly(
                new DeathClaimSlaAlarm(claim.id(), DeathSlaLevel.BREACH, Duration.ofHours(4)));
    }

    @Test
    void should_reject_a_null_dependency() {
        // given / when / then
        assertThatThrownBy(() -> new CheckDeathClaimSlaService(
                        null, alarms, raisedAlarms, clock, SLA, WARNING_FRACTION))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("claimRepository");
        assertThatThrownBy(() -> new CheckDeathClaimSlaService(
                        claims, null, raisedAlarms, clock, SLA, WARNING_FRACTION))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("alarmPort");
        assertThatThrownBy(() -> new CheckDeathClaimSlaService(
                        claims, alarms, null, clock, SLA, WARNING_FRACTION))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("raisedAlarms");
        assertThatThrownBy(() -> new CheckDeathClaimSlaService(
                        claims, alarms, raisedAlarms, null, SLA, WARNING_FRACTION))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock");
        assertThatThrownBy(() -> new CheckDeathClaimSlaService(
                        claims, alarms, raisedAlarms, clock, null, WARNING_FRACTION))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("duration");
    }

    private static Claim deathClaim() {
        return Claim.accept(
                ClaimType.DEATH,
                "C-1",
                "P-1",
                new BigDecimal("150.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 10),
                "DC-1",
                Clock.fixed(ACCEPTED_AT, ZoneOffset.UTC));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class FakeClaimRepository implements ClaimRepositoryPort {

        private List<Claim> open = List.of();

        private void open(Claim claim) {
            open = List.of(claim);
        }

        @Override
        public void save(Claim claim) {
            throw new UnsupportedOperationException("save");
        }

        @Override
        public Optional<Claim> findById(ClaimId id) {
            return Optional.empty();
        }

        @Override
        public List<Claim> findOpenDeathClaims() {
            return open;
        }
    }

    private static final class RecordingAlarmPort implements DeathClaimSlaAlarmPort {

        private final List<DeathClaimSlaAlarm> raised = new ArrayList<>();

        @Override
        public void raise(DeathClaimSlaAlarm alarm) {
            raised.add(alarm);
        }

        private List<DeathClaimSlaAlarm> raised() {
            return List.copyOf(raised);
        }
    }
}
