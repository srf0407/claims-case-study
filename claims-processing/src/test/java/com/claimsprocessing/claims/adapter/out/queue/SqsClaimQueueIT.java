package com.claimsprocessing.claims.adapter.out.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.out.ClaimQueuedMessage;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.localstack.LocalStackSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import tools.jackson.databind.json.JsonMapper;

class SqsClaimQueueIT {

    private static final String DEATH_QUEUE = "claims-death";
    private static final String STANDARD_QUEUE = "claims-standard";
    private static final ClaimId FUNERAL_ID =
            new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final ClaimId DEATH_ID =
            new ClaimId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final ClaimId DELETED_ID =
            new ClaimId(UUID.fromString("33333333-3333-3333-3333-333333333333"));

    private static SqsClient sqsClient;
    private static SqsClaimQueue queue;

    @BeforeAll
    static void start() {
        sqsClient = LocalStackSupport.sqsClient();
        queue = new SqsClaimQueue(
                sqsClient,
                DEATH_QUEUE,
                STANDARD_QUEUE,
                new SqsClaimMessageMapper(JsonMapper.builder().build()));
    }

    @AfterAll
    static void stop() {
        if (sqsClient != null) {
            sqsClient.close();
        }
    }

    @BeforeEach
    void emptyQueues() {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (queue.receive().isEmpty()) {
                return;
            }
        }
        throw new AssertionError("queues still returned a message after 20 receives");
    }

    @Test
    void should_create_both_queues_with_a_30_second_visibility_timeout() {
        // when
        String deathTimeout = visibilityTimeout(DEATH_QUEUE);
        String standardTimeout = visibilityTimeout(STANDARD_QUEUE);

        // then
        assertThat(deathTimeout).isEqualTo("30");
        assertThat(standardTimeout).isEqualTo("30");
    }

    @Test
    void should_return_a_later_death_message_before_an_earlier_standard_message() {
        // given
        ClaimQueuedMessage funeral = new ClaimQueuedMessage(FUNERAL_ID, ClaimType.FUNERAL);
        ClaimQueuedMessage death = new ClaimQueuedMessage(DEATH_ID, ClaimType.DEATH);
        queue.enqueue(funeral);
        queue.enqueue(death);

        // when
        Optional<ClaimQueuedMessage> first = queue.receive();
        Optional<ClaimQueuedMessage> second = queue.receive();

        // then
        assertThat(first).contains(death);
        assertThat(second).contains(funeral);
    }

    @Test
    void should_return_empty_when_both_queues_are_empty() {
        // when
        Optional<ClaimQueuedMessage> received = queue.receive();

        // then
        assertThat(received).isEmpty();
    }

    @Test
    void should_delete_a_received_message() {
        // given
        ClaimQueuedMessage message = new ClaimQueuedMessage(DELETED_ID, ClaimType.FUNERAL);
        queue.enqueue(message);

        // when
        Optional<ClaimQueuedMessage> first = queue.receive();
        Optional<ClaimQueuedMessage> second = queue.receive();

        // then
        assertThat(first).contains(message);
        assertThat(second).isEmpty();
    }

    private static String visibilityTimeout(String queueName) {
        String queueUrl = sqsClient
                .getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build())
                .queueUrl();
        return sqsClient
                .getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.VISIBILITY_TIMEOUT)
                        .build())
                .attributes()
                .get(QueueAttributeName.VISIBILITY_TIMEOUT);
    }
}
