package com.claimsprocessing.claims.adapter.out.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.claimsprocessing.claims.application.port.out.ClaimQueuedMessage;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.localstack.LocalStackSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SqsClaimQueueIT {

    private static final String DEATH_QUEUE = "claims-death";
    private static final String STANDARD_QUEUE = "claims-standard";
    private static final String DLQ = "claims-dlq";
    private static final String POISON = "poison";
    private static final JsonMapper JSON = JsonMapper.builder().build();
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
                new SqsClaimMessageMapper(JsonMapper.builder().build()),
                Clock.systemUTC(),
                Duration.ofSeconds(30));
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
            Optional<ClaimQueuedMessage> received = queue.receive();
            if (received.isEmpty()) {
                return;
            }
            queue.acknowledge(received.get());
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
    void should_set_redrive_on_both_main_queues_to_claims_dlq() {
        // when
        String dlqArn = queueArn(DLQ);
        JsonNode deathRedrive = redrivePolicy(DEATH_QUEUE);
        JsonNode standardRedrive = redrivePolicy(STANDARD_QUEUE);

        // then
        assertThat(deathRedrive.get("deadLetterTargetArn").asText()).isEqualTo(dlqArn);
        assertThat(deathRedrive.get("maxReceiveCount").asText()).isEqualTo("4");
        assertThat(standardRedrive.get("deadLetterTargetArn").asText()).isEqualTo(dlqArn);
        assertThat(standardRedrive.get("maxReceiveCount").asText()).isEqualTo("4");
    }

    @Test
    void should_move_an_undeleted_message_to_claims_dlq_after_four_receives() {
        // given
        String standardUrl = queueUrl(STANDARD_QUEUE);
        String dlqUrl = queueUrl(DLQ);
        Message lastSource = null;
        try {
            setVisibility(standardUrl, "1");
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(standardUrl)
                    .messageBody(POISON)
                    .build());

            // when
            for (int received = 0; received < 4; received++) {
                lastSource = awaitBody(standardUrl, POISON, 10);
                assertThat(lastSource.body()).isEqualTo(POISON);
            }
            // Redrive runs on the receive after maxReceiveCount is exceeded.
            List<Message> fifth = receive(standardUrl, 10);

            // then
            assertThat(fifth).isEmpty();
            assertThat(awaitBody(dlqUrl, POISON, 10).body()).isEqualTo(POISON);
        } finally {
            try {
                release(standardUrl, lastSource);
                deleteMatching(standardUrl, POISON);
                deleteMatching(dlqUrl, POISON);
            } finally {
                setVisibility(standardUrl, "30");
            }
        }
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
        queue.acknowledge(first.orElseThrow());
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
    void should_return_the_same_message_until_it_is_acknowledged() {
        // given
        ClaimQueuedMessage message = new ClaimQueuedMessage(DELETED_ID, ClaimType.FUNERAL);
        queue.enqueue(message);

        // when
        Optional<ClaimQueuedMessage> first = queue.receive();
        Optional<ClaimQueuedMessage> second = queue.receive();
        queue.acknowledge(first.orElseThrow());
        Optional<ClaimQueuedMessage> third = queue.receive();

        // then
        assertThat(first).contains(message);
        assertThat(second).contains(message);
        assertThat(third).isEmpty();
    }

    private static String visibilityTimeout(String queueName) {
        return queueAttributes(queueName, QueueAttributeName.VISIBILITY_TIMEOUT)
                .get(QueueAttributeName.VISIBILITY_TIMEOUT);
    }

    private static String queueArn(String queueName) {
        return queueAttributes(queueName, QueueAttributeName.QUEUE_ARN).get(QueueAttributeName.QUEUE_ARN);
    }

    private static JsonNode redrivePolicy(String queueName) {
        String raw = queueAttributes(queueName, QueueAttributeName.REDRIVE_POLICY)
                .get(QueueAttributeName.REDRIVE_POLICY);
        return JSON.readTree(raw);
    }

    private static Map<QueueAttributeName, String> queueAttributes(
            String queueName, QueueAttributeName attributeName) {
        return sqsClient
                .getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl(queueName))
                        .attributeNames(attributeName)
                        .build())
                .attributes();
    }

    private static String queueUrl(String queueName) {
        return sqsClient
                .getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build())
                .queueUrl();
    }

    private static void setVisibility(String queueUrl, String seconds) {
        sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(Map.of(QueueAttributeName.VISIBILITY_TIMEOUT, seconds))
                .build());
    }

    private static Message awaitBody(String queueUrl, String body, int timeoutSeconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            int remainingSeconds = (int) Math.max(
                    1, TimeUnit.NANOSECONDS.toSeconds(deadline - System.nanoTime()));
            for (Message message : receive(queueUrl, Math.min(remainingSeconds, 10))) {
                if (body.equals(message.body())) {
                    return message;
                }
            }
        }
        throw new AssertionError("queue did not return " + body);
    }

    private static List<Message> receive(String queueUrl, int waitSeconds) {
        List<Message> messages = sqsClient
                .receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(1)
                        .waitTimeSeconds(waitSeconds)
                        .build())
                .messages();
        if (messages == null) {
            return List.of();
        }
        return messages;
    }

    private static void release(String queueUrl, Message message) {
        if (message == null) {
            return;
        }
        try {
            sqsClient.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .visibilityTimeout(0)
                    .build());
        } catch (RuntimeException exception) {
            // The message may already have moved to the dead-letter queue.
        }
    }

    private static void deleteMatching(String queueUrl, String body) {
        for (Message message : receive(queueUrl, 2)) {
            if (body.equals(message.body())) {
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
            }
        }
    }
}
