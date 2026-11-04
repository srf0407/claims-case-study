package com.claimsprocessing.claims.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "claims.payment")
public record PaymentProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
