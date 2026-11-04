package com.claimsprocessing.claims.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.claimsprocessing.claims.application.port.in.ApplyPaymentStatusCommand;
import com.claimsprocessing.claims.application.port.in.PaymentOutcome;
import com.claimsprocessing.claims.domain.ClaimId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class PaymentCallbackMapperTest {

    private static final UUID CLAIM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final PaymentCallbackMapper mapper = new PaymentCallbackMapper(JsonMapper.builder().build());

    @Test
    void should_map_a_callback_to_a_command() {
        // given
        var request = new PaymentCallbackRequest(CLAIM_ID.toString(), PaymentOutcome.PAID, CLAIM_ID.toString());

        // when
        ApplyPaymentStatusCommand command = mapper.toCommand(request);

        // then
        assertThat(command).isEqualTo(new ApplyPaymentStatusCommand(
                new ClaimId(CLAIM_ID), PaymentOutcome.PAID, CLAIM_ID.toString()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void should_reject_a_blank_or_missing_claim_id(String claimId) {
        // given
        var request = new PaymentCallbackRequest(claimId, PaymentOutcome.PAID, CLAIM_ID.toString());

        // when / then
        assertThatThrownBy(() -> mapper.toCommand(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("claimId");
    }

    @Test
    void should_reject_a_missing_outcome() {
        // given
        var request = new PaymentCallbackRequest(CLAIM_ID.toString(), null, CLAIM_ID.toString());

        // when / then
        assertThatThrownBy(() -> mapper.toCommand(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outcome");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void should_reject_a_blank_or_missing_idempotency_key(String idempotencyKey) {
        // given
        var request = new PaymentCallbackRequest(CLAIM_ID.toString(), PaymentOutcome.PAID, idempotencyKey);

        // when / then
        assertThatThrownBy(() -> mapper.toCommand(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotencyKey");
    }

    @Test
    void should_reject_a_null_request_to_the_mapper() {
        // given / when / then
        assertThatThrownBy(() -> mapper.toCommand(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request");
    }
}
