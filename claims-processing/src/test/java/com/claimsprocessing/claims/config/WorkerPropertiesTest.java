package com.claimsprocessing.claims.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class WorkerPropertiesTest {

    @Test
    void should_reject_a_null_worker_poll_interval() {
        // given / when / then
        assertThatThrownBy(() -> new WorkerProperties(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("pollInterval");
    }

    @ParameterizedTest
    @MethodSource("nonPositiveIntervals")
    void should_reject_a_worker_poll_interval_that_is_not_positive(Duration pollInterval) {
        // given / when / then
        assertThatThrownBy(() -> new WorkerProperties(pollInterval))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pollInterval must be positive");
    }

    private static Stream<Duration> nonPositiveIntervals() {
        return Stream.of(Duration.ZERO, Duration.ofSeconds(-1));
    }
}
