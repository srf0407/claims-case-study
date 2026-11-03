package com.claimsprocessing.claims.domain;

import java.time.Instant;
import java.util.Objects;

public record ClaimAccepted(Instant occurredAt) implements ClaimEvent {
    public ClaimAccepted {
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
