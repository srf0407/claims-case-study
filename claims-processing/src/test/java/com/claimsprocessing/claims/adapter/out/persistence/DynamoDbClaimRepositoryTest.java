package com.claimsprocessing.claims.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.out.ClaimRepositoryUnavailableException;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

@ExtendWith(MockitoExtension.class)
class DynamoDbClaimRepositoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    private static final Clock LATER = Clock.fixed(Instant.parse("2026-01-15T11:00:00Z"), ZoneOffset.UTC);
    private static final String TABLE = "claims";

    @Mock
    private DynamoDbClient dynamoDbClient;

    private final ClaimItemMapper mapper = new ClaimItemMapper();
    private DynamoDbClaimRepository repository;
    private final List<PutItemRequest> puts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = new DynamoDbClaimRepository(dynamoDbClient, TABLE, mapper);
    }

    @Test
    void should_return_the_saved_claim_for_its_id() {
        // given
        Claim claim = funeral();
        rememberPuts();
        returnLastPut();

        // when
        repository.save(claim);
        var found = repository.findById(claim.id());

        // then
        assertThat(found).isPresent();
        assertThat(found.orElseThrow()).usingRecursiveComparison().isEqualTo(claim);
        assertThat(puts).hasSize(1);
        assertThat(puts.getFirst().tableName()).isEqualTo(TABLE);
        assertThat(puts.getFirst().item().get("id").s()).isEqualTo(claim.id().value().toString());
        ArgumentCaptor<GetItemRequest> get = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(get.capture());
        assertThat(get.getValue().tableName()).isEqualTo(TABLE);
        assertThat(get.getValue().key().get("id").s()).isEqualTo(claim.id().value().toString());
    }

    @Test
    void should_put_the_new_item_when_the_same_id_is_saved_again() {
        // given
        Claim accepted = funeral();
        Claim awaitingAnalyst = accepted.transitionTo(ClaimStatus.AWAITING_ANALYST, LATER);
        rememberPuts();
        returnLastPut();

        // when
        repository.save(accepted);
        repository.save(awaitingAnalyst);
        var found = repository.findById(accepted.id());

        // then
        assertThat(puts).hasSize(2);
        assertThat(puts.get(1).item().get("id").s()).isEqualTo(accepted.id().value().toString());
        assertThat(puts.get(1).item().get("status").s()).isEqualTo("AWAITING_ANALYST");
        assertThat(found).isPresent();
        assertThat(found.orElseThrow()).usingRecursiveComparison().isEqualTo(awaitingAnalyst);
    }

    @Test
    void should_return_empty_for_an_unknown_id() {
        // given
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().build());

        // when
        var found = repository.findById(new ClaimId(UUID.randomUUID()));

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void should_return_empty_for_a_null_id_without_calling_dynamodb() {
        // when
        var found = repository.findById(null);

        // then
        assertThat(found).isEmpty();
        verifyNoInteractions(dynamoDbClient);
    }

    @Test
    void should_reject_a_null_claim() {
        // when / then
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void should_throw_repository_unavailable_when_the_save_fails() {
        // given
        RuntimeException failure = new RuntimeException("put-down");
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> repository.save(funeral()))
                .isInstanceOf(ClaimRepositoryUnavailableException.class)
                .hasCause(failure);
    }

    @Test
    void should_return_open_death_claims_from_a_filtered_scan() {
        // given
        Claim accepted = death();
        Claim awaitingAnalyst = death().transitionTo(ClaimStatus.AWAITING_ANALYST, LATER);
        when(dynamoDbClient.scan(any(ScanRequest.class))).thenReturn(ScanResponse.builder()
                .items(List.of(mapper.toAttributes(accepted), mapper.toAttributes(awaitingAnalyst)))
                .build());

        // when
        List<Claim> open = repository.findOpenDeathClaims();

        // then
        assertThat(open).hasSize(2);
        assertThat(open.get(0)).usingRecursiveComparison().isEqualTo(accepted);
        assertThat(open.get(1)).usingRecursiveComparison().isEqualTo(awaitingAnalyst);
        ArgumentCaptor<ScanRequest> scan = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient).scan(scan.capture());
        assertThat(scan.getValue().tableName()).isEqualTo(TABLE);
        assertThat(scan.getValue().filterExpression())
                .isEqualTo("#claimType = :death AND (#status = :accepted OR #status = :awaiting)");
        assertThat(scan.getValue().expressionAttributeNames())
                .containsExactlyInAnyOrderEntriesOf(Map.of("#claimType", "claimType", "#status", "status"));
        assertThat(scan.getValue().expressionAttributeValues().get(":death").s()).isEqualTo("DEATH");
        assertThat(scan.getValue().expressionAttributeValues().get(":accepted").s()).isEqualTo("ACCEPTED");
        assertThat(scan.getValue().expressionAttributeValues().get(":awaiting").s()).isEqualTo("AWAITING_ANALYST");
        assertThat(scan.getValue().exclusiveStartKey()).isEmpty();
    }

    @Test
    void should_return_an_empty_list_when_the_scan_has_no_items() {
        // given
        when(dynamoDbClient.scan(any(ScanRequest.class))).thenReturn(ScanResponse.builder().build());

        // when
        List<Claim> open = repository.findOpenDeathClaims();

        // then
        assertThat(open).isEmpty();
    }

    @Test
    void should_read_every_scan_page() {
        // given
        Claim accepted = death();
        Claim awaitingAnalyst = death().transitionTo(ClaimStatus.AWAITING_ANALYST, LATER);
        Map<String, AttributeValue> pageKey =
                Map.of("id", AttributeValue.builder().s(accepted.id().value().toString()).build());
        when(dynamoDbClient.scan(any(ScanRequest.class))).thenReturn(
                ScanResponse.builder()
                        .items(List.of(mapper.toAttributes(accepted)))
                        .lastEvaluatedKey(pageKey)
                        .build(),
                ScanResponse.builder().items(List.of(mapper.toAttributes(awaitingAnalyst))).build());

        // when
        List<Claim> open = repository.findOpenDeathClaims();

        // then
        assertThat(open).hasSize(2);
        assertThat(open.get(0)).usingRecursiveComparison().isEqualTo(accepted);
        assertThat(open.get(1)).usingRecursiveComparison().isEqualTo(awaitingAnalyst);
        ArgumentCaptor<ScanRequest> scans = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient, times(2)).scan(scans.capture());
        assertThat(scans.getAllValues().get(0).exclusiveStartKey()).isEmpty();
        assertThat(scans.getAllValues().get(1).exclusiveStartKey()).isEqualTo(pageKey);
    }

    @Test
    void should_throw_repository_unavailable_when_the_scan_fails() {
        // given
        RuntimeException failure = new RuntimeException("scan-down");
        when(dynamoDbClient.scan(any(ScanRequest.class))).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> repository.findOpenDeathClaims())
                .isInstanceOf(ClaimRepositoryUnavailableException.class)
                .hasCause(failure);
    }

    @Test
    void should_throw_repository_unavailable_when_the_find_fails() {
        // given
        RuntimeException failure = new RuntimeException("get-down");
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> repository.findById(new ClaimId(UUID.randomUUID())))
                .isInstanceOf(ClaimRepositoryUnavailableException.class)
                .hasCause(failure);
    }

    @Test
    void should_replace_only_when_the_stored_status_matches() {
        // given
        rememberPuts();
        Claim awaitingAnalyst = funeral().transitionTo(ClaimStatus.AWAITING_ANALYST, CLOCK);

        // when
        boolean replaced = repository.replaceIfStatus(awaitingAnalyst, ClaimStatus.ACCEPTED);

        // then
        assertThat(replaced).isTrue();
        assertThat(puts).hasSize(1);
        PutItemRequest put = puts.getFirst();
        assertThat(put.conditionExpression()).isEqualTo("#status = :expected");
        assertThat(put.expressionAttributeNames()).containsEntry("#status", "status");
        assertThat(put.expressionAttributeValues().get(":expected").s()).isEqualTo("ACCEPTED");
        assertThat(put.item().get("status").s()).isEqualTo("AWAITING_ANALYST");
    }

    @Test
    void should_return_false_when_the_status_condition_fails() {
        // given
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.builder().message("status changed").build());
        Claim awaitingAnalyst = funeral().transitionTo(ClaimStatus.AWAITING_ANALYST, CLOCK);

        // when
        boolean replaced = repository.replaceIfStatus(awaitingAnalyst, ClaimStatus.ACCEPTED);

        // then
        assertThat(replaced).isFalse();
    }

    private void rememberPuts() {
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenAnswer(invocation -> {
            puts.add(invocation.getArgument(0));
            return PutItemResponse.builder().build();
        });
    }

    private void returnLastPut() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
            if (puts.isEmpty()) {
                return GetItemResponse.builder().build();
            }
            return GetItemResponse.builder().item(puts.getLast().item()).build();
        });
    }

    private static Claim death() {
        return Claim.accept(
                ClaimType.DEATH,
                "client-1",
                "POL-1",
                new BigDecimal("250.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 10),
                "DC-1",
                CLOCK);
    }

    private static Claim funeral() {
        return Claim.accept(
                ClaimType.FUNERAL,
                "client-1",
                "POL-1",
                new BigDecimal("10.50"),
                LocalDate.of(2026, 1, 15),
                null,
                null,
                CLOCK);
    }
}
