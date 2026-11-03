package com.claimsprocessing.claims.application.port.in;

import com.claimsprocessing.claims.domain.ClaimId;

public record ApplyPaymentStatusCommand(ClaimId claimId, PaymentOutcome outcome, String idempotencyKey) {
}
