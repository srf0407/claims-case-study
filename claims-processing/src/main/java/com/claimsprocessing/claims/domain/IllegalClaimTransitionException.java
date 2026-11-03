package com.claimsprocessing.claims.domain;

public final class IllegalClaimTransitionException extends RuntimeException {
    public IllegalClaimTransitionException(ClaimStatus from, ClaimStatus to) {
        super("Cannot transition from " + from.name() + " to " + to.name());
    }
}
