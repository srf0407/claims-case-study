package com.claimsprocessing.claims.application;

import java.util.Objects;

public final class ClaimWorkerResultMapper {

    public ClaimWorkerResult map(StraightThroughResult straightThroughResult) {
        Objects.requireNonNull(straightThroughResult, "straightThroughResult");
        return switch (straightThroughResult) {
            case PAYMENT_INITIATED -> ClaimWorkerResult.PAYMENT_INITIATED;
            case SENT_TO_ANALYST -> ClaimWorkerResult.SENT_TO_ANALYST;
        };
    }
}
