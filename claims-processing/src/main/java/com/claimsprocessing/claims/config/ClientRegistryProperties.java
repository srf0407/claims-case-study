package com.claimsprocessing.claims.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "claims.client-registry")
public record ClientRegistryProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
