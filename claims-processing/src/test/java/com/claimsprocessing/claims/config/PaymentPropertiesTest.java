package com.claimsprocessing.claims.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentPropertiesTest {

    @Test
    void should_reject_a_missing_callback_secret() {
        // given / when / then
        assertThatThrownBy(() -> paymentProperties(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("PAYMENT_CALLBACK_SECRET");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void should_reject_a_blank_callback_secret(String callbackSecret) {
        // given / when / then
        assertThatThrownBy(() -> paymentProperties(callbackSecret))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PAYMENT_CALLBACK_SECRET must be set");
    }

    @Test
    void should_keep_a_valid_callback_secret() {
        // given / when
        PaymentProperties properties = paymentProperties("dev-callback-secret");

        // then
        assertThat(properties.callbackSecret()).isEqualTo("dev-callback-secret");
    }

    private static PaymentProperties paymentProperties(String callbackSecret) {
        return new PaymentProperties(
                "http://localhost:8083", Duration.ofSeconds(2), Duration.ofSeconds(5), callbackSecret);
    }
}
