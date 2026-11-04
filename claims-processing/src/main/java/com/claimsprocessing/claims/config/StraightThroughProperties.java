package com.claimsprocessing.claims.config;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "claims.straight-through")
public record StraightThroughProperties(BigDecimal limit) {

    public StraightThroughProperties {
        Objects.requireNonNull(limit, "limit");
        if (limit.signum() <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
