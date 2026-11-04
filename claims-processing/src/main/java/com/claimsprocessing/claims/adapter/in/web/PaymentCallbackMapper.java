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
        requireText(request.claimId(), "claimId");
        if (request.outcome() == null) {
            throw new IllegalArgumentException("outcome");
        }
        requireText(request.idempotencyKey(), "idempotencyKey");
        return new ApplyPaymentStatusCommand(
                new ClaimId(parseClaimId(request.claimId())),
                request.outcome(),
                request.idempotencyKey());
    }

    private static UUID parseClaimId(String claimId) {
        try {
            return UUID.fromString(claimId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("claimId uuid", exception);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name);
        }
    }

    public PaymentCallbackResponse toResponse(PaymentStatusResult result) {
        Objects.requireNonNull(result, "result");
        return new PaymentCallbackResponse(result);
    }
}
