package com.claimsprocessing.claims.adapter.out.queue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.claimsprocessing.claims.application.port.out.ClaimQueuePort;
import com.claimsprocessing.claims.application.port.out.ClaimQueueUnavailableException;
import com.claimsprocessing.claims.application.port.out.ClaimQueuedMessage;
import com.claimsprocessing.claims.domain.ClaimType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public final class SqsClaimQueue implements ClaimQueuePort {

    private static final Logger log = LoggerFactory.getLogger(SqsClaimQueue.class);

    private final SqsClient sqsClient;
    private final SqsClaimMessageMapper mapper;
    private final Clock clock;
    private final int visibilityTimeoutSeconds;
    private final String deathQueueUrl;
    private final String standardQueueUrl;
    private ClaimQueuedMessage inflightMessage;
    private String inflightQueueUrl;
    private String inflightReceiptHandle;
    private Instant inflightVisibleUntil;

    public SqsClaimQueue(
            SqsClient sqsClient,
            String deathQueueName,
            String standardQueueName,
            SqsClaimMessageMapper mapper,
            Clock clock,
            Duration visibilityTimeout) {
        this.sqsClient = Objects.requireNonNull(sqsClient, "sqsClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds(visibilityTimeout);
        Objects.requireNonNull(deathQueueName, "deathQueueName");
        Objects.requireNonNull(standardQueueName, "standardQueueName");
        this.deathQueueUrl = queueUrl(deathQueueName);
        this.standardQueueUrl = queueUrl(standardQueueName);
    }

    @Override
    public void enqueue(ClaimQueuedMessage message) {
        Objects.requireNonNull(message, "message");
        String queueUrl = message.claimType() == ClaimType.DEATH ? deathQueueUrl : standardQueueUrl;
        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(mapper.toBody(message))
                    .build());
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public synchronized Optional<ClaimQueuedMessage> receive() {
        if (inflightMessage != null) {
            if (clock.instant().isBefore(inflightVisibleUntil)) {
                refreshVisibility();
                return Optional.of(inflightMessage);
            }
            clearInflight();
        }
        Optional<Message> deathMessage = poll(deathQueueUrl);
        if (deathMessage.isPresent()) {
            return Optional.of(hold(deathQueueUrl, deathMessage.get()));
        }
        Optional<Message> standardMessage = poll(standardQueueUrl);
        if (standardMessage.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(hold(standardQueueUrl, standardMessage.get()));
    }

    @Override
    public synchronized void acknowledge(ClaimQueuedMessage message) {
        Objects.requireNonNull(message, "message");
        if (inflightMessage == null) {
            return;
        }
        if (!inflightMessage.equals(message)) {
            throw new IllegalStateException("Claim queue message is not in flight");
        }
        String queueUrl = inflightQueueUrl;
        String receiptHandle = inflightReceiptHandle;
        try {
            delete(queueUrl, receiptHandle);
        } catch (ClaimQueueUnavailableException exception) {
            clearInflight();
            throw exception;
        }
        clearInflight();
    }

    @Override
    public synchronized void release(ClaimQueuedMessage message) {
        Objects.requireNonNull(message, "message");
        if (inflightMessage == null) {
            return;
        }
        if (!inflightMessage.equals(message)) {
            throw new IllegalStateException("Claim queue message is not in flight");
        }
        clearInflight();
    }

    private Optional<Message> poll(String queueUrl) {
        try {
            ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(0)
                    .build());
            List<Message> messages = response.messages();
            if (messages == null || messages.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(messages.getFirst());
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private ClaimQueuedMessage hold(String queueUrl, Message message) {
        ClaimQueuedMessage mapped = read(message);
        inflightMessage = mapped;
        inflightQueueUrl = queueUrl;
        inflightReceiptHandle = message.receiptHandle();
        inflightVisibleUntil = clock.instant().plusSeconds(visibilityTimeoutSeconds);
        return mapped;
    }

    private void refreshVisibility() {
        try {
            sqsClient.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                    .queueUrl(inflightQueueUrl)
                    .receiptHandle(inflightReceiptHandle)
                    .visibilityTimeout(visibilityTimeoutSeconds)
                    .build());
        } catch (RuntimeException exception) {
            clearInflight();
            throw unavailable(exception);
        }
        inflightVisibleUntil = clock.instant().plusSeconds(visibilityTimeoutSeconds);
    }

    private void clearInflight() {
        inflightMessage = null;
        inflightQueueUrl = null;
        inflightReceiptHandle = null;
        inflightVisibleUntil = null;
    }

    private ClaimQueuedMessage read(Message message) {
        try {
            return mapper.toMessage(message.body());
        } catch (RuntimeException exception) {
            log.warn("Poison claim message {}", message.messageId());
            throw new ClaimQueueUnavailableException("Claim queue message cannot be read", exception);
        }
    }

    private void delete(String queueUrl, String receiptHandle) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .build());
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private String queueUrl(String queueName) {
        try {
            return sqsClient
                    .getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build())
                    .queueUrl();
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private static int visibilityTimeoutSeconds(Duration visibilityTimeout) {
        Objects.requireNonNull(visibilityTimeout, "visibilityTimeout");
        long seconds = visibilityTimeout.toSeconds();
        if (seconds < 1 || seconds > 43_200) {
            throw new IllegalArgumentException("visibilityTimeout");
        }
        return (int) seconds;
    }

    private static ClaimQueueUnavailableException unavailable(RuntimeException exception) {
        return new ClaimQueueUnavailableException("Claim queue is unavailable", exception);
    }
}
