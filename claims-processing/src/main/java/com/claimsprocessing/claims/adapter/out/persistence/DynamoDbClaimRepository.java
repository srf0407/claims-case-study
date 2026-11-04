package com.claimsprocessing.claims.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
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
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

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

    @Override
    public List<Claim> findOpenDeathClaims() {
        try {
            List<Claim> claims = new ArrayList<>();
            Map<String, AttributeValue> startKey = null;
            do {
                ScanResponse response = dynamoDbClient.scan(openDeathClaims(startKey));
                for (Map<String, AttributeValue> item : response.items()) {
                    claims.add(mapper.toClaim(item));
                }
                startKey = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
            } while (startKey != null);
            return List.copyOf(claims);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private ScanRequest openDeathClaims(Map<String, AttributeValue> startKey) {
        ScanRequest.Builder request = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("#claimType = :death AND (#status = :accepted OR #status = :awaiting)")
                .expressionAttributeNames(Map.of("#claimType", "claimType", "#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":death", string("DEATH"),
                        ":accepted", string("ACCEPTED"),
                        ":awaiting", string("AWAITING_ANALYST")));
        if (startKey != null) {
            request.exclusiveStartKey(startKey);
        }
        return request.build();
    }

    private static AttributeValue string(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static ClaimRepositoryUnavailableException unavailable(RuntimeException exception) {
        return new ClaimRepositoryUnavailableException("Claim repository is unavailable", exception);
    }
}
