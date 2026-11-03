package com.claimsprocessing.claims.application.port.out;

public final class ClaimQueueUnavailableException extends RuntimeException {

    public ClaimQueueUnavailableException(String message) {
        super(message);
    }
}
