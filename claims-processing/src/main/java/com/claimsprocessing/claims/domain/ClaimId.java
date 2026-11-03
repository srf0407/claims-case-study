package com.claimsprocessing.claims.domain;

import java.util.UUID;

public record ClaimId(UUID value) {
    public ClaimId {
        if (value == null) {
            throw new InvalidClaimException("Claim id is required");
        }
    }
}
