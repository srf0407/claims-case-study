package com.claimsprocessing.claims.application;

import java.util.Objects;

import com.claimsprocessing.claims.application.port.out.ClaimQueuedMessage;
import com.claimsprocessing.claims.domain.Claim;

public final class ClaimQueuedMessageMapper {

    public ClaimQueuedMessage map(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        return new ClaimQueuedMessage(claim.id(), claim.claimType());
    }
}
