package com.claimsprocessing.claims.application.port.out;

public final class ClaimRepositoryUnavailableException extends RuntimeException {

    public ClaimRepositoryUnavailableException(String message) {
        super(message);
    }

    public ClaimRepositoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
