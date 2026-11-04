package com.claimsprocessing.claims.adapter.out.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryUnavailableException;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public final class DynamoDbClaimRepository implements ClaimRepositoryPort {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final ClaimItemMapper mapper;

    public DynamoDbClaimRepository(DynamoDbClient dynamoDbClient, String tableName, ClaimItemMapper mapper) {
        this.dynamoDbClient = Objects.requireNonNull(dynamoDbClient, "dynamoDbClient");
        this.tableName = Objects.requireNonNull(tableName, "tableName");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void save(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        try {
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(mapper.toAttributes(claim))
                    .build());
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public Optional<Claim> findById(ClaimId id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("id", AttributeValue.builder().s(id.value().toString()).build()))
                    .build());
            Map<String, AttributeValue> item = response.item();
            if (item == null || item.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(mapper.toClaim(item));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private static ClaimRepositoryUnavailableException unavailable(RuntimeException exception) {
        return new ClaimRepositoryUnavailableException("Claim repository is unavailable", exception);
    }
}
