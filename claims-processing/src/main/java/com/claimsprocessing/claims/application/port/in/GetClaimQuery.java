package com.claimsprocessing.claims.application.port.in;

import com.claimsprocessing.claims.domain.ClaimId;

public record GetClaimQuery(ClaimId claimId) {
}
