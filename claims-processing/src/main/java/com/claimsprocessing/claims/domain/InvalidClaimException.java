package com.claimsprocessing.claims.domain;

public final class InvalidClaimException extends RuntimeException {
    public InvalidClaimException(String message) {
        super(message);
    }
}
