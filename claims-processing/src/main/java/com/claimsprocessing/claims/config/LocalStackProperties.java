package com.claimsprocessing.claims.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "claims.localstack")
public record LocalStackProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String deathQueueName,
        String standardQueueName,
        String tableName) {
}
