package com.claimsprocessing.claims.application;

public enum ClaimWorkerResult {
    QUEUE_EMPTY,
    REJECTED,
    PAYMENT_INITIATED,
    SENT_TO_ANALYST
}
