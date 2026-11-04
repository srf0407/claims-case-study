package com.claimsprocessing.claims.adapter.in.web;

import java.util.Objects;
import java.util.UUID;

import com.claimsprocessing.claims.application.PaymentStatusResult;
import com.claimsprocessing.claims.application.port.in.ApplyPaymentStatusCommand;
import com.claimsprocessing.claims.domain.ClaimId;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class PaymentCallbackMapper {

    private final ObjectMapper objectMapper;

    public PaymentCallbackMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public PaymentCallbackRequest read(byte[] body) throws JacksonException {
        Objects.requireNonNull(body, "body");
        return objectMapper.readValue(body, PaymentCallbackRequest.class);
    }

    public ApplyPaymentStatusCommand toCommand(PaymentCallbackRequest request) {
        Objects.requireNonNull(request, "request");
        return new ApplyPaymentStatusCommand(
                new ClaimId(UUID.fromString(request.claimId())),
                request.outcome(),
                request.idempotencyKey());
    }

    public PaymentCallbackResponse toResponse(PaymentStatusResult result) {
        Objects.requireNonNull(result, "result");
        return new PaymentCallbackResponse(result);
    }
}
