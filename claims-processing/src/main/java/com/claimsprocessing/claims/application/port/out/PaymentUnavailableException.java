package com.claimsprocessing.claims.application.port.out;

public final class PaymentUnavailableException extends RuntimeException {

    public PaymentUnavailableException(String message) {
        super(message);
    }

    public PaymentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
