package com.claimsprocessing.claims.application;

public final class ClaimNotAwaitingAnalystException extends RuntimeException {

    public ClaimNotAwaitingAnalystException() {
        super("Claim is not awaiting an analyst");
    }
}
