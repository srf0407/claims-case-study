package com.claimsprocessing.claims.adapter.in.web;

import java.util.UUID;

import com.claimsprocessing.claims.domain.ClaimStatus;

public record DecideClaimResponse(UUID claimId, ClaimStatus status) {
}
