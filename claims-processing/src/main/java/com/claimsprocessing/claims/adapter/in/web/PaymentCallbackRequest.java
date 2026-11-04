package com.claimsprocessing.claims.adapter.in.web;

import com.claimsprocessing.claims.application.port.in.PaymentOutcome;

public record PaymentCallbackRequest(String claimId, PaymentOutcome outcome, String idempotencyKey) {
}
