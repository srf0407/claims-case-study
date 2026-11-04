package com.claimsprocessing.claims.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.CheckDeathClaimSlaUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.DeathClaimSlaAlarm;
import com.claimsprocessing.claims.application.port.out.DeathClaimSlaAlarmPort;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimAccepted;
import com.claimsprocessing.claims.domain.DeathSlaLevel;
import com.claimsprocessing.claims.domain.InvalidClaimException;

public final class CheckDeathClaimSlaService implements CheckDeathClaimSlaUseCase {

    private final ClaimRepositoryPort claimRepository;
    private final DeathClaimSlaAlarmPort alarmPort;
    private final RaisedDeathClaimSlaAlarms raisedAlarms;
    private final Clock clock;
    private final Duration duration;
    private final double warningFraction;

    public CheckDeathClaimSlaService(
            ClaimRepositoryPort claimRepository,
            DeathClaimSlaAlarmPort alarmPort,
            RaisedDeathClaimSlaAlarms raisedAlarms,
            Clock clock,
            Duration duration,
            double warningFraction) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.alarmPort = Objects.requireNonNull(alarmPort, "alarmPort");
        this.raisedAlarms = Objects.requireNonNull(raisedAlarms, "raisedAlarms");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.duration = Objects.requireNonNull(duration, "duration");
        this.warningFraction = warningFraction;
    }

    @Override
    public void check() {
        Instant now = clock.instant();
        for (Claim claim : claimRepository.findOpenDeathClaims()) {
            DeathSlaLevel level = claim.openDeathSlaLevel(now, duration, warningFraction);
            if (level == DeathSlaLevel.BREACH && !raisedAlarms.breachRaised(claim.id())) {
                raisedAlarms.recordBreach(claim.id());
                alarmPort.raise(new DeathClaimSlaAlarm(claim.id(), level, elapsedSinceAccept(claim, now)));
            } else if (level == DeathSlaLevel.WARNING && !raisedAlarms.warningRaised(claim.id())) {
                raisedAlarms.recordWarning(claim.id());
                alarmPort.raise(new DeathClaimSlaAlarm(claim.id(), level, elapsedSinceAccept(claim, now)));
            }
        }
    }

    private static Duration elapsedSinceAccept(Claim claim, Instant now) {
        Instant acceptedAt = claim.events().stream()
                .filter(ClaimAccepted.class::isInstance)
                .map(ClaimAccepted.class::cast)
                .map(ClaimAccepted::occurredAt)
                .findFirst()
                .orElseThrow(() -> new InvalidClaimException("accept time is required"));
        return Duration.between(acceptedAt, now);
    }
}
