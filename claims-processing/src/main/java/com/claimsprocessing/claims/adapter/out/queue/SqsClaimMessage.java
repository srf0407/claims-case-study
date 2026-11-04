package com.claimsprocessing.claims.adapter.out.queue;

public record SqsClaimMessage(String claimId, String claimType) {
}
