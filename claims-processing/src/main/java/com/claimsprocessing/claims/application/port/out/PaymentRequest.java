package com.claimsprocessing.claims.application.port.out;

import java.math.BigDecimal;

import com.claimsprocessing.claims.domain.ClaimId;

public record PaymentRequest(
        String idempotencyKey,
        ClaimId claimId,
        String clientId,
        String policyNumber,
        BigDecimal amount) {
}
