package com.claimsprocessing.claims.application;

import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.PaymentOutcome;
import com.claimsprocessing.claims.domain.ClaimStatus;

public final class PaymentOutcomeMapper {

    public ClaimStatus map(PaymentOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        return switch (outcome) {
            case PAID -> ClaimStatus.PAID;
            case FAILED -> ClaimStatus.PAYMENT_FAILED;
        };
    }
}
