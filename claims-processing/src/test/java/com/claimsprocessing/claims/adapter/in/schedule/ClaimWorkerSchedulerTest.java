package com.claimsprocessing.claims.adapter.in.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.claimsprocessing.claims.application.ClaimWorkerResult;
import com.claimsprocessing.claims.application.port.in.ClaimWorkerUseCase;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

class ClaimWorkerSchedulerTest {

    @Test
    void should_drain_until_the_queue_is_empty() {
        // given
        var useCase = new ScriptedUseCase();
        useCase.willReturn(
                ClaimWorkerResult.SENT_TO_ANALYST,
                ClaimWorkerResult.REJECTED,
                ClaimWorkerResult.PAYMENT_INITIATED,
                ClaimWorkerResult.QUEUE_EMPTY);
        var scheduler = new ClaimWorkerScheduler(useCase);

        // when
        scheduler.poll();

        // then
        assertThat(useCase.calls()).isEqualTo(4);
    }

    @Test
    void should_stop_when_the_queue_is_empty() {
        // given
        var useCase = new ScriptedUseCase();
        useCase.willReturn(ClaimWorkerResult.QUEUE_EMPTY);
        var scheduler = new ClaimWorkerScheduler(useCase);

        // when
        scheduler.poll();

        // then
        assertThat(useCase.calls()).isEqualTo(1);
    }

    @Test
    void should_cap_a_tick_at_ten_messages() {
        // given
        var useCase = new ScriptedUseCase();
        useCase.willReturn(eleven(ClaimWorkerResult.PAYMENT_INITIATED));
        var scheduler = new ClaimWorkerScheduler(useCase);

        // when
        scheduler.poll();

        // then
        assertThat(useCase.calls()).isEqualTo(10);
    }

    @Test
    void should_stop_the_tick_when_process_throws() {
        // given
        var useCase = new ScriptedUseCase();
        useCase.willReturn(
                ClaimWorkerResult.REJECTED, ClaimWorkerResult.PAYMENT_INITIATED, ClaimWorkerResult.SENT_TO_ANALYST);
        useCase.willThrowOnCall(3, new IllegalStateException("down"));
        var scheduler = new ClaimWorkerScheduler(useCase);

        // when
        scheduler.poll();

        // then
        assertThat(useCase.calls()).isEqualTo(3);
    }

    @Test
    void should_log_a_process_failure_at_error() {
        // given
        var failure = new IllegalStateException("down");
        var useCase = new ScriptedUseCase();
        useCase.willThrowOnCall(1, failure);
        var scheduler = new ClaimWorkerScheduler(useCase);
        ListAppender<ILoggingEvent> logs = attach();

        // when
        try {
            scheduler.poll();
        } finally {
            detach(logs);
        }

        // then
        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy().getClassName()).isEqualTo(failure.getClass().getName());
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo(failure.getMessage());
        assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
    }

    @Test
    void should_reject_a_null_use_case() {
        // given / when / then
        assertThatThrownBy(() -> new ClaimWorkerScheduler(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("useCase");
    }

    @Test
    void should_wait_for_the_poll_interval_before_the_first_tick() throws NoSuchMethodException {
        // given
        Method poll = ClaimWorkerScheduler.class.getMethod("poll");

        // when
        Scheduled scheduled = poll.getAnnotation(Scheduled.class);

        // then
        assertThat(scheduled.fixedDelayString()).isEqualTo("${claims.worker.poll-interval}");
        assertThat(scheduled.initialDelayString()).isEqualTo("${claims.worker.poll-interval}");
    }

    private static ClaimWorkerResult[] eleven(ClaimWorkerResult result) {
        var results = new ClaimWorkerResult[11];
        for (int i = 0; i < results.length; i++) {
            results[i] = result;
        }
        return results;
    }

    private static ListAppender<ILoggingEvent> attach() {
        Logger logger = (Logger) LoggerFactory.getLogger(ClaimWorkerScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detach(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(ClaimWorkerScheduler.class);
        logger.detachAppender(appender);
    }

    private static final class ScriptedUseCase implements ClaimWorkerUseCase {

        private final Queue<ClaimWorkerResult> results = new ArrayDeque<>();
        private int calls;
        private int failOnCall;
        private RuntimeException failure;

        private void willReturn(ClaimWorkerResult... next) {
            results.addAll(List.of(next));
        }

        private void willThrowOnCall(int call, RuntimeException exception) {
            this.failOnCall = call;
            this.failure = exception;
        }

        private int calls() {
            return calls;
        }

        @Override
        public ClaimWorkerResult process() {
            calls++;
            if (calls == failOnCall) {
                throw failure;
            }
            return results.remove();
        }
    }
}
