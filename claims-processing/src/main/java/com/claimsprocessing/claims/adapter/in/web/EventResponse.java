package com.claimsprocessing.claims.adapter.in.web;

import java.time.Instant;

import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.RejectionReason;

public record EventResponse(
        String type,
        Instant occurredAt,
        ClaimStatus from,
        ClaimStatus to,
        RejectionReason reason) {
}
