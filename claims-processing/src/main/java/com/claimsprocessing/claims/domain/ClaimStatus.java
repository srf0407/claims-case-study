package com.claimsprocessing.claims.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ClaimStatus {
    ACCEPTED,
    REJECTED,
    AWAITING_ANALYST,
    APPROVED,
    PAYMENT_PENDING,
    PAID,
    PAYMENT_FAILED;

    boolean allows(ClaimStatus next) {
        return allowedTargets().contains(next);
    }

    private Set<ClaimStatus> allowedTargets() {
        return switch (this) {
            case ACCEPTED -> EnumSet.of(AWAITING_ANALYST, PAYMENT_PENDING);
            case AWAITING_ANALYST -> EnumSet.of(APPROVED);
            case APPROVED -> EnumSet.of(PAYMENT_PENDING);
            case PAYMENT_PENDING -> EnumSet.of(PAID, PAYMENT_FAILED);
            case REJECTED, PAID, PAYMENT_FAILED -> EnumSet.noneOf(ClaimStatus.class);
        };
    }
}
