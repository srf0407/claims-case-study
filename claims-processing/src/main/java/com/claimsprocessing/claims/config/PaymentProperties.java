package com.claimsprocessing.claims.config;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "claims.payment")
public record PaymentProperties(
        String baseUrl, Duration connectTimeout, Duration readTimeout, String callbackSecret) {

    public PaymentProperties {
        Objects.requireNonNull(callbackSecret, "PAYMENT_CALLBACK_SECRET");
        if (callbackSecret.isBlank()) {
            throw new IllegalArgumentException("PAYMENT_CALLBACK_SECRET must be set");
        }
    }
}
