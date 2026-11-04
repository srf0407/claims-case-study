package com.claimsprocessing.claims.application.port.out;

public final class PolicyManagerUnavailableException extends RuntimeException {

    public PolicyManagerUnavailableException(String message) {
        super(message);
    }

    public PolicyManagerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
