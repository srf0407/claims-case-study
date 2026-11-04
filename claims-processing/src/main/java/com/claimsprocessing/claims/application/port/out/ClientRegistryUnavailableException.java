package com.claimsprocessing.claims.application.port.out;

public final class ClientRegistryUnavailableException extends RuntimeException {

    public ClientRegistryUnavailableException(String message) {
        super(message);
    }

    public ClientRegistryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
