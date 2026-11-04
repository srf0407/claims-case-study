package com.claimsprocessing.claims.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.domain.RejectionReason;
import com.claimsprocessing.claims.localstack.LocalStackSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

class DynamoDbClaimRepositoryIT {

    private static final String TABLE = "claims";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    private static final Clock LATER = Clock.fixed(Instant.parse("2026-01-15T11:00:00Z"), ZoneOffset.UTC);
    private static final ClaimId UNKNOWN_ID =
            new ClaimId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

    private static DynamoDbClient dynamoDbClient;
    private static DynamoDbClaimRepository repository;

    @BeforeAll
    static void start() {
        dynamoDbClient = LocalStackSupport.dynamoDbClient();
        repository = new DynamoDbClaimRepository(dynamoDbClient, TABLE, new ClaimItemMapper());
    }

    @AfterAll
    static void stop() {
        if (dynamoDbClient != null) {
            dynamoDbClient.close();
        }
    }

    @Test
    void should_create_the_claims_table_with_id_as_its_only_key() {
        // when
        TableDescription table = dynamoDbClient
                .describeTable(DescribeTableRequest.builder().tableName(TABLE).build())
                .table();

        // then
        assertThat(table.tableName()).isEqualTo(TABLE);
        assertThat(table.keySchema()).singleElement().satisfies(key -> {
            assertThat(key.attributeName()).isEqualTo("id");
            assertThat(key.keyType()).isEqualTo(KeyType.HASH);
        });
        assertThat(table.attributeDefinitions()).singleElement().satisfies(attribute -> {
            assertThat(attribute.attributeName()).isEqualTo("id");
            assertThat(attribute.attributeType()).isEqualTo(ScalarAttributeType.S);
        });
        assertThat(table.billingModeSummary().billingMode()).isEqualTo(BillingMode.PAY_PER_REQUEST);
    }

    @Test
    void should_return_a_saved_rejected_death_claim() {
        // given
        Claim claim = rejectedDeath();

        // when
        repository.save(claim);

        // then
        assertThat(repository.findById(claim.id())).get().usingRecursiveComparison().isEqualTo(claim);
    }

    @Test
    void should_replace_the_claim_when_the_same_id_is_saved_again() {
        // given
        Claim claim = funeral();
        repository.save(claim);
        Claim updated = claim.transitionTo(ClaimStatus.AWAITING_ANALYST, LATER);

        // when
        repository.save(updated);

        // then
        assertThat(repository.findById(claim.id())).get().usingRecursiveComparison().isEqualTo(updated);
    }

    @Test
    void should_return_only_accepted_and_awaiting_analyst_death_claims() {
        // given
        Claim acceptedDeath = death();
        Claim awaitingAnalyst = death().transitionTo(ClaimStatus.AWAITING_ANALYST, LATER);
        Claim approved = death()
                .transitionTo(ClaimStatus.AWAITING_ANALYST, LATER)
                .transitionTo(ClaimStatus.APPROVED, LATER);
        Claim rejected = death().reject(RejectionReason.UNKNOWN_CLIENT, LATER);
        Claim paymentPending = death().transitionTo(ClaimStatus.PAYMENT_PENDING, LATER);
        Claim paid = death()
                .transitionTo(ClaimStatus.PAYMENT_PENDING, LATER)
                .transitionTo(ClaimStatus.PAID, LATER);
        Claim paymentFailed = death()
                .transitionTo(ClaimStatus.PAYMENT_PENDING, LATER)
                .transitionTo(ClaimStatus.PAYMENT_FAILED, LATER);
        Claim funeral = funeral();
        Claim disability = Claim.accept(
                ClaimType.DISABILITY,
                "client-2",
                "POL-2",
                new BigDecimal("80.00"),
                LocalDate.of(2026, 1, 15),
                null,
                null,
                CLOCK).transitionTo(ClaimStatus.AWAITING_ANALYST, LATER);
        repository.save(acceptedDeath);
        repository.save(awaitingAnalyst);
        repository.save(approved);
        repository.save(rejected);
        repository.save(paymentPending);
        repository.save(paid);
        repository.save(paymentFailed);
        repository.save(funeral);
        repository.save(disability);

        // when
        List<Claim> open = repository.findOpenDeathClaims();

        // then
        assertThat(open)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(acceptedDeath, awaitingAnalyst);
    }

    @Test
    void should_return_empty_for_an_unknown_id() {
        // when
        Optional<Claim> found = repository.findById(UNKNOWN_ID);

        // then
        assertThat(found).isEmpty();
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

    private static Claim rejectedDeath() {
        Claim accepted = Claim.accept(
                ClaimType.DEATH,
                "client-1",
                "POL-1",
                new BigDecimal("250.00"),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 10),
                "DC-1",
                CLOCK);
        return accepted.reject(RejectionReason.UNKNOWN_CLIENT, LATER);
    }
}
