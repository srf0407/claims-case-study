package com.claimsprocessing.claims.application.port.out;

import java.time.Duration;
import java.util.Objects;

import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.DeathSlaLevel;

public record DeathClaimSlaAlarm(ClaimId claimId, DeathSlaLevel level, Duration elapsed) {

    public DeathClaimSlaAlarm {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(elapsed, "elapsed");
        if (level == DeathSlaLevel.ON_TIME) {
            throw new IllegalArgumentException("alarm level must be WARNING or BREACH");
        }
    }
}
