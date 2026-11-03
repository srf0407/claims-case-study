package com.claimsprocessing.claims.domain;

public sealed interface ClaimEvent permits ClaimAccepted, ClaimStatusChanged {
}
