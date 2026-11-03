package com.claimsprocessing.claims.application;

import com.claimsprocessing.claims.domain.ClaimId;

public final class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(ClaimId claimId) {
        super("Claim not found: " + claimId.value());
    }
}
