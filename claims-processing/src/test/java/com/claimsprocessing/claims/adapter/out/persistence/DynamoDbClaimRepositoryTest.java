package com.claimsprocessing.claims.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

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
    void should_throw_repository_unavailable_when_the_find_fails() {
        // given
        RuntimeException failure = new RuntimeException("get-down");
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> repository.findById(new ClaimId(UUID.randomUUID())))
                .isInstanceOf(ClaimRepositoryUnavailableException.class)
                .hasCause(failure);
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
