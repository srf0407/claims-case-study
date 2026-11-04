package com.claimsprocessing.claims.adapter.out.payment;

import java.math.BigDecimal;

public record PaymentInitiateRequest(
        String claimId,
        String clientId,
        String policyNumber,
        BigDecimal amount) {
}
