package com.claimsprocessing.claims.config;

import java.net.URI;

import com.claimsprocessing.claims.adapter.out.persistence.ClaimItemMapper;
import com.claimsprocessing.claims.adapter.out.persistence.DynamoDbClaimRepository;
import com.claimsprocessing.claims.adapter.out.queue.SqsClaimMessageMapper;
import com.claimsprocessing.claims.adapter.out.queue.SqsClaimQueue;
import com.claimsprocessing.claims.application.port.out.ClaimQueuePort;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@Profile("localstack")
public class LocalStackConfig {

    @Bean
    SqsClient sqsClient(LocalStackProperties properties) {
        return SqsClient.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    DynamoDbClient dynamoDbClient(LocalStackProperties properties) {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    SqsClaimMessageMapper sqsClaimMessageMapper(ObjectMapper objectMapper) {
        return new SqsClaimMessageMapper(objectMapper);
    }

    @Bean
    ClaimItemMapper claimItemMapper() {
        return new ClaimItemMapper();
    }

    @Bean
    ClaimQueuePort sqsClaimQueuePort(
            SqsClient sqsClient, LocalStackProperties properties, SqsClaimMessageMapper mapper) {
        return new SqsClaimQueue(
                sqsClient, properties.deathQueueName(), properties.standardQueueName(), mapper);
    }

    @Bean
    ClaimRepositoryPort dynamoDbClaimRepositoryPort(
            DynamoDbClient dynamoDbClient, LocalStackProperties properties, ClaimItemMapper mapper) {
        return new DynamoDbClaimRepository(dynamoDbClient, properties.tableName(), mapper);
    }

    private static StaticCredentialsProvider credentials(LocalStackProperties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}
