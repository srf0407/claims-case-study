package com.claimsprocessing.claims.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StraightThroughPropertiesTest {

    @Test
    void should_reject_a_null_straight_through_limit() {
        // given / when / then
        assertThatThrownBy(() -> new StraightThroughProperties(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("limit");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void should_reject_a_straight_through_limit_that_is_not_positive(String limit) {
        // given / when / then
        assertThatThrownBy(() -> new StraightThroughProperties(new BigDecimal(limit)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be positive");
    }
}
