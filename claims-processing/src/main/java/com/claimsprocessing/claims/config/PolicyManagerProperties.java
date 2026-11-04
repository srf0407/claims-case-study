package com.claimsprocessing.claims.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "claims.policy-manager")
public record PolicyManagerProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
