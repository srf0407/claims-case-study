package com.claimsprocessing.claims.config;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "claims.death-sla")
public record DeathClaimSlaProperties(Duration duration, double warningFraction, Duration checkInterval) {

    public DeathClaimSlaProperties {
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(checkInterval, "checkInterval");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        if (!(warningFraction > 0.0 && warningFraction < 1.0)) {
            throw new IllegalArgumentException("warningFraction must be between 0 and 1 exclusive");
        }
        if (checkInterval.isZero() || checkInterval.isNegative()) {
            throw new IllegalArgumentException("checkInterval must be positive");
        }
    }
}
