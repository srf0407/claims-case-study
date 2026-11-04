package com.claimsprocessing.claims.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DeathClaimSlaPropertiesTest {

    @ParameterizedTest
    @MethodSource("badProperties")
    void should_reject_a_bad_death_sla_property(
            Duration duration, double warningFraction, Duration checkInterval, String message) {
        // given / when / then
        assertThatThrownBy(() -> new DeathClaimSlaProperties(duration, warningFraction, checkInterval))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    @Test
    void should_reject_a_null_duration_or_check_interval() {
        // given / when / then
        assertThatThrownBy(() -> new DeathClaimSlaProperties(null, 0.75, Duration.ofMinutes(1)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("duration");
        assertThatThrownBy(() -> new DeathClaimSlaProperties(Duration.ofHours(4), 0.75, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("checkInterval");
    }

    private static Stream<Arguments> badProperties() {
        Duration duration = Duration.ofHours(4);
        Duration interval = Duration.ofMinutes(1);
        return Stream.of(
                Arguments.of(Duration.ZERO, 0.75, interval, "duration must be positive"),
                Arguments.of(Duration.ofHours(-1), 0.75, interval, "duration must be positive"),
                Arguments.of(duration, 0.0, interval, "warningFraction must be between 0 and 1 exclusive"),
                Arguments.of(duration, 1.0, interval, "warningFraction must be between 0 and 1 exclusive"),
                Arguments.of(duration, -0.01, interval, "warningFraction must be between 0 and 1 exclusive"),
                Arguments.of(duration, 1.01, interval, "warningFraction must be between 0 and 1 exclusive"),
                Arguments.of(duration, 0.75, Duration.ZERO, "checkInterval must be positive"),
                Arguments.of(duration, 0.75, Duration.ofMinutes(-1), "checkInterval must be positive"));
    }
}
