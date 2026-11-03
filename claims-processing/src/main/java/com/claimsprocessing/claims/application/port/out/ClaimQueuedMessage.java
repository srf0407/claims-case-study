package com.claimsprocessing.claims.application.port.out;

import java.util.Objects;

import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;

public record ClaimQueuedMessage(ClaimId claimId, ClaimType claimType) {

    public ClaimQueuedMessage {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(claimType, "claimType");
    }
}
