package com.claimsprocessing.claims.adapter.out.queue;

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
    private final String deathQueueUrl;
    private final String standardQueueUrl;

    public SqsClaimQueue(
            SqsClient sqsClient,
            String deathQueueName,
            String standardQueueName,
            SqsClaimMessageMapper mapper) {
        this.sqsClient = Objects.requireNonNull(sqsClient, "sqsClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
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
    public Optional<ClaimQueuedMessage> receive() {
        Optional<Message> deathMessage = poll(deathQueueUrl);
        if (deathMessage.isPresent()) {
            return Optional.of(readAndDelete(deathQueueUrl, deathMessage.get()));
        }
        Optional<Message> standardMessage = poll(standardQueueUrl);
        if (standardMessage.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(readAndDelete(standardQueueUrl, standardMessage.get()));
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

    private ClaimQueuedMessage readAndDelete(String queueUrl, Message message) {
        ClaimQueuedMessage mapped = read(message);
        delete(queueUrl, message);
        return mapped;
    }

    private ClaimQueuedMessage read(Message message) {
        try {
            return mapper.toMessage(message.body());
        } catch (RuntimeException exception) {
            log.warn("Poison claim message {}", message.messageId());
            throw new ClaimQueueUnavailableException("Claim queue message cannot be read", exception);
        }
    }

    private void delete(String queueUrl, Message message) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
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

    private static ClaimQueueUnavailableException unavailable(RuntimeException exception) {
        return new ClaimQueueUnavailableException("Claim queue is unavailable", exception);
    }
}
