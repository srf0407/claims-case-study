package com.claimsprocessing.claims.domain;

import java.time.Instant;
import java.util.Objects;

public record ClaimStatusChanged(ClaimStatus from, ClaimStatus to, Instant occurredAt) implements ClaimEvent {
    public ClaimStatusChanged {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
