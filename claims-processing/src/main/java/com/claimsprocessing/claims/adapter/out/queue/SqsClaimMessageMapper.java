package com.claimsprocessing.claims.adapter.out.queue;

import java.util.Objects;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.out.ClaimQueuedMessage;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class SqsClaimMessageMapper {

    private final ObjectMapper objectMapper;

    public SqsClaimMessageMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public String toBody(ClaimQueuedMessage message) {
        Objects.requireNonNull(message, "message");
        try {
            return objectMapper.writeValueAsString(new SqsClaimMessage(
                    message.claimId().value().toString(), message.claimType().name()));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Claim queue message cannot be written", exception);
        }
    }

    public ClaimQueuedMessage toMessage(String body) {
        SqsClaimMessage dto = read(body);
        if (dto == null || dto.claimId() == null || dto.claimType() == null) {
            throw new IllegalArgumentException("Claim queue message cannot be read");
        }
        try {
            return new ClaimQueuedMessage(
                    new ClaimId(UUID.fromString(dto.claimId())), ClaimType.valueOf(dto.claimType()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Claim queue message cannot be read", exception);
        }
    }

    private SqsClaimMessage read(String body) {
        try {
            return objectMapper.readValue(body, SqsClaimMessage.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Claim queue message cannot be read", exception);
        }
    }
}
