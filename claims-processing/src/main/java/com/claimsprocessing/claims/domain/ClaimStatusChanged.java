package com.claimsprocessing.claims.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ClaimStatusChanged(
        ClaimStatus from,
        ClaimStatus to,
        Optional<RejectionReason> reason,
        Instant occurredAt) implements ClaimEvent {

    public ClaimStatusChanged {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (to == ClaimStatus.REJECTED && reason.isEmpty()) {
            throw new InvalidClaimException("rejection reason is required");
        }
        if (to != ClaimStatus.REJECTED && reason.isPresent()) {
            throw new InvalidClaimException("rejection reason is only allowed when rejecting");
        }
    }
}
