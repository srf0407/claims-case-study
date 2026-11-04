package com.claimsprocessing.claims.adapter.out.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.claimsprocessing.claims.application.port.out.ClaimQueueUnavailableException;
import com.claimsprocessing.claims.application.port.out.ClaimQueuedMessage;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SqsClaimQueueTest {

    private static final String DEATH_QUEUE = "claims-death";
    private static final String STANDARD_QUEUE = "claims-standard";
    private static final String DEATH_URL = "http://localhost:4566/000000000000/claims-death";
    private static final String STANDARD_URL = "http://localhost:4566/000000000000/claims-standard";
    private static final ClaimId CLAIM_ID = new ClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @Mock
    private SqsClient sqsClient;

    private SqsClaimMessageMapper mapper;
    private SqsClaimQueue queue;

    @BeforeEach
    void setUp() {
        mapper = new SqsClaimMessageMapper(JsonMapper.builder().build());
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class))).thenAnswer(invocation -> {
            GetQueueUrlRequest request = invocation.getArgument(0);
            String queueUrl = switch (request.queueName()) {
                case DEATH_QUEUE -> DEATH_URL;
                case STANDARD_QUEUE -> STANDARD_URL;
                default -> throw new IllegalArgumentException(request.queueName());
            };
            return GetQueueUrlResponse.builder().queueUrl(queueUrl).build();
        });
        queue = newQueue(sqsClient, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    @Test
    void should_send_a_death_message_to_the_death_queue() {
        // given
        ClaimQueuedMessage message = new ClaimQueuedMessage(CLAIM_ID, ClaimType.DEATH);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().build());

        // when
        queue.enqueue(message);

        // then
        ArgumentCaptor<SendMessageRequest> sent = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(sent.capture());
        assertThat(sent.getValue().queueUrl()).isEqualTo(DEATH_URL);
        assertThat(sent.getValue().messageBody()).isEqualTo(mapper.toBody(message));
    }

    @Test
    void should_send_a_non_death_message_to_the_standard_queue() {
        // given
        ClaimQueuedMessage message = new ClaimQueuedMessage(CLAIM_ID, ClaimType.FUNERAL);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().build());

        // when
        queue.enqueue(message);

        // then
        ArgumentCaptor<SendMessageRequest> sent = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(sent.capture());
        assertThat(sent.getValue().queueUrl()).isEqualTo(STANDARD_URL);
        assertThat(sent.getValue().messageBody()).isEqualTo(mapper.toBody(message));
    }

    @Test
    void should_return_a_death_message_without_reading_the_standard_queue_and_delete_it_on_acknowledge() {
        // given
        ClaimQueuedMessage message = new ClaimQueuedMessage(CLAIM_ID, ClaimType.DEATH);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            ReceiveMessageRequest request = invocation.getArgument(0);
            if (DEATH_URL.equals(request.queueUrl())) {
                return response(sqsMessage(mapper.toBody(message)));
            }
            return emptyResponse();
        });
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(DeleteMessageResponse.builder().build());

        // when
        var received = queue.receive();

        // then
        assertThat(received).contains(message);
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        queue.acknowledge(received.orElseThrow());
        ArgumentCaptor<ReceiveMessageRequest> polls = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsClient).receiveMessage(polls.capture());
        assertShortPoll(polls.getValue(), DEATH_URL);
        verify(sqsClient, never()).receiveMessage(aRequest(request -> STANDARD_URL.equals(request.queueUrl())));
        ArgumentCaptor<DeleteMessageRequest> deleted = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqsClient).deleteMessage(deleted.capture());
        assertThat(deleted.getValue().queueUrl()).isEqualTo(DEATH_URL);
        assertThat(deleted.getValue().receiptHandle()).isEqualTo("rh-1");
    }

    @Test
    void should_return_a_standard_message_when_the_death_queue_is_empty_and_delete_it_on_acknowledge() {
        // given
        ClaimQueuedMessage message = new ClaimQueuedMessage(CLAIM_ID, ClaimType.FUNERAL);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            ReceiveMessageRequest request = invocation.getArgument(0);
            if (STANDARD_URL.equals(request.queueUrl())) {
                return response(sqsMessage(mapper.toBody(message)));
            }
            return emptyResponse();
        });
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(DeleteMessageResponse.builder().build());

        // when
        var received = queue.receive();

        // then
        assertThat(received).contains(message);
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        queue.acknowledge(received.orElseThrow());
        InOrder order = inOrder(sqsClient);
        order.verify(sqsClient).receiveMessage(aRequest(request -> shortPoll(request, DEATH_URL)));
        order.verify(sqsClient).receiveMessage(aRequest(request -> shortPoll(request, STANDARD_URL)));
        ArgumentCaptor<DeleteMessageRequest> deleted = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqsClient).deleteMessage(deleted.capture());
        assertThat(deleted.getValue().queueUrl()).isEqualTo(STANDARD_URL);
        assertThat(deleted.getValue().receiptHandle()).isEqualTo("rh-1");
    }

    @Test
    void should_release_a_standard_message_without_deleting_it_and_then_read_the_death_queue() {
        // given
        ClaimId deathId = new ClaimId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        ClaimQueuedMessage funeral = new ClaimQueuedMessage(CLAIM_ID, ClaimType.FUNERAL);
        ClaimQueuedMessage death = new ClaimQueuedMessage(deathId, ClaimType.DEATH);
        int[] deathPolls = {0};
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            ReceiveMessageRequest request = invocation.getArgument(0);
            if (DEATH_URL.equals(request.queueUrl())) {
                deathPolls[0]++;
                if (deathPolls[0] == 1) {
                    return emptyResponse();
                }
                return response(sqsMessage(mapper.toBody(death), "rh-death"));
            }
            return response(sqsMessage(mapper.toBody(funeral), "rh-funeral"));
        });

        // when
        var received = queue.receive();
        queue.release(received.orElseThrow());
        var next = queue.receive();

        // then
        assertThat(received).contains(funeral);
        assertThat(next).contains(death);
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
    }

    @Test
    void should_return_empty_when_both_queues_are_empty() {
        // given
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(emptyResponse());

        // when
        var received = queue.receive();

        // then
        assertThat(received).isEmpty();
        verify(sqsClient).receiveMessage(aRequest(request -> DEATH_URL.equals(request.queueUrl())));
        verify(sqsClient).receiveMessage(aRequest(request -> STANDARD_URL.equals(request.queueUrl())));
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void should_fail_the_receive_when_the_death_queue_call_fails() {
        // given
        RuntimeException failure = new RuntimeException("death-down");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenThrow(failure);

        // when / then
        assertThatThrownBy(queue::receive)
                .isInstanceOf(ClaimQueueUnavailableException.class)
                .hasCause(failure);
        verify(sqsClient, never()).receiveMessage(aRequest(request -> STANDARD_URL.equals(request.queueUrl())));
    }

    @Test
    void should_fail_the_receive_when_the_standard_queue_call_fails() {
        // given
        RuntimeException failure = new RuntimeException("standard-down");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            ReceiveMessageRequest request = invocation.getArgument(0);
            if (DEATH_URL.equals(request.queueUrl())) {
                return emptyResponse();
            }
            throw failure;
        });

        // when / then
        assertThatThrownBy(queue::receive)
                .isInstanceOf(ClaimQueueUnavailableException.class)
                .hasCause(failure);
    }

    @Test
    void should_throw_queue_unavailable_when_the_send_fails() {
        // given
        RuntimeException failure = new RuntimeException("send-down");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> queue.enqueue(new ClaimQueuedMessage(CLAIM_ID, ClaimType.FUNERAL)))
                .isInstanceOf(ClaimQueueUnavailableException.class)
                .hasCause(failure);
    }

    @Test
    void should_clear_the_in_flight_message_when_the_delete_fails() {
        // given
        RuntimeException failure = new RuntimeException("delete-down");
        ClaimQueuedMessage message = new ClaimQueuedMessage(CLAIM_ID, ClaimType.DEATH);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(response(sqsMessage(mapper.toBody(message))));
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class))).thenThrow(failure);
        var received = queue.receive();

        // when / then
        assertThatThrownBy(() -> queue.acknowledge(received.orElseThrow()))
                .isInstanceOf(ClaimQueueUnavailableException.class)
                .hasCause(failure);
        assertThat(queue.receive()).contains(message);
        verify(sqsClient, org.mockito.Mockito.times(2)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    void should_refresh_the_receipt_handle_while_the_message_is_still_invisible() {
        // given
        MutableClock clock = new MutableClock(Instant.parse("2026-11-04T18:00:00Z"));
        SqsClaimQueue timedQueue = newQueue(sqsClient, clock, Duration.ofSeconds(30));
        ClaimQueuedMessage message = new ClaimQueuedMessage(CLAIM_ID, ClaimType.DEATH);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(response(sqsMessage(mapper.toBody(message))));
        timedQueue.receive();
        clock.advance(Duration.ofSeconds(5));

        // when
        var again = timedQueue.receive();

        // then
        assertThat(again).contains(message);
        ArgumentCaptor<ChangeMessageVisibilityRequest> refreshed =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqsClient).changeMessageVisibility(refreshed.capture());
        assertThat(refreshed.getValue().queueUrl()).isEqualTo(DEATH_URL);
        assertThat(refreshed.getValue().receiptHandle()).isEqualTo("rh-1");
        assertThat(refreshed.getValue().visibilityTimeout()).isEqualTo(30);
        verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    void should_poll_again_when_the_visibility_timeout_has_elapsed() {
        // given
        MutableClock clock = new MutableClock(Instant.parse("2026-11-04T18:00:00Z"));
        SqsClaimQueue timedQueue = newQueue(sqsClient, clock, Duration.ofSeconds(30));
        ClaimQueuedMessage message = new ClaimQueuedMessage(CLAIM_ID, ClaimType.DEATH);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(response(sqsMessage(mapper.toBody(message), "rh-1")))
                .thenReturn(response(sqsMessage(mapper.toBody(message), "rh-2")));
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(DeleteMessageResponse.builder().build());
        timedQueue.receive();
        clock.advance(Duration.ofSeconds(30));

        // when
        var again = timedQueue.receive();
        timedQueue.acknowledge(again.orElseThrow());

        // then
        assertThat(again).contains(message);
        verify(sqsClient, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        ArgumentCaptor<DeleteMessageRequest> deleted = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqsClient).deleteMessage(deleted.capture());
        assertThat(deleted.getValue().receiptHandle()).isEqualTo("rh-2");
    }

    @ParameterizedTest
    @MethodSource("poisonBodies")
    void should_log_a_poison_message_and_leave_it_on_the_queue(String body) {
        // given
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(response(sqsMessage(body)));
        ListAppender<ILoggingEvent> logs = attachQueueLog();

        // when
        try {
            assertThatThrownBy(queue::receive).isInstanceOf(ClaimQueueUnavailableException.class);
        } finally {
            detachQueueLog(logs);
        }

        // then
        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("msg-123");
            assertThat(event.getFormattedMessage()).doesNotContain(body);
            assertThat(event.getThrowableProxy()).isNull();
        });
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(sqsClient, never()).receiveMessage(aRequest(request -> STANDARD_URL.equals(request.queueUrl())));
    }

    @Test
    void should_reject_a_null_message() {
        // when / then
        assertThatThrownBy(() -> queue.enqueue(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void should_throw_queue_unavailable_when_a_queue_url_cannot_be_resolved() {
        // given
        SqsClient client = mock(SqsClient.class);
        RuntimeException failure = new RuntimeException("no-queue");
        when(client.getQueueUrl(any(GetQueueUrlRequest.class))).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> newQueue(client, Clock.systemUTC(), Duration.ofSeconds(30)))
                .isInstanceOf(ClaimQueueUnavailableException.class)
                .hasCause(failure);
    }

    private static Stream<String> poisonBodies() {
        return Stream.of(
                "not-json-body-secret",
                "{\"claimType\":\"FUNERAL\"}",
                "{\"claimId\":\"11111111-1111-1111-1111-111111111111\"}",
                "{\"claimId\":\"11111111-1111-1111-1111-111111111111\",\"claimType\":\"PET\"}");
    }

    private static ReceiveMessageRequest aRequest(Predicate<ReceiveMessageRequest> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }

    private static void assertShortPoll(ReceiveMessageRequest request, String queueUrl) {
        assertThat(request.queueUrl()).isEqualTo(queueUrl);
        assertThat(request.maxNumberOfMessages()).isEqualTo(1);
        assertThat(request.waitTimeSeconds()).isEqualTo(0);
        assertThat(request.visibilityTimeout()).isNull();
    }

    private static boolean shortPoll(ReceiveMessageRequest request, String queueUrl) {
        return queueUrl.equals(request.queueUrl())
                && Integer.valueOf(1).equals(request.maxNumberOfMessages())
                && Integer.valueOf(0).equals(request.waitTimeSeconds())
                && request.visibilityTimeout() == null;
    }

    private static ReceiveMessageResponse response(Message message) {
        return ReceiveMessageResponse.builder().messages(List.of(message)).build();
    }

    private static ReceiveMessageResponse emptyResponse() {
        return ReceiveMessageResponse.builder().messages(List.of()).build();
    }

    private SqsClaimQueue newQueue(SqsClient client, Clock clock, Duration visibilityTimeout) {
        return new SqsClaimQueue(client, DEATH_QUEUE, STANDARD_QUEUE, mapper, clock, visibilityTimeout);
    }

    private static Message sqsMessage(String body) {
        return sqsMessage(body, "rh-1");
    }

    private static Message sqsMessage(String body, String receiptHandle) {
        return Message.builder().messageId("msg-123").receiptHandle(receiptHandle).body(body).build();
    }

    private static ListAppender<ILoggingEvent> attachQueueLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(SqsClaimQueue.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachQueueLog(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(SqsClaimQueue.class);
        logger.detachAppender(appender);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
