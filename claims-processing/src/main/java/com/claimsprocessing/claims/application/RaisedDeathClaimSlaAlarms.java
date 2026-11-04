package com.claimsprocessing.claims.application;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.claimsprocessing.claims.domain.ClaimId;

public final class RaisedDeathClaimSlaAlarms {

    private final Set<ClaimId> warnings = ConcurrentHashMap.newKeySet();
    private final Set<ClaimId> breaches = ConcurrentHashMap.newKeySet();

    public boolean warningRaised(ClaimId claimId) {
        Objects.requireNonNull(claimId, "claimId");
        return warnings.contains(claimId);
    }

    public boolean breachRaised(ClaimId claimId) {
        Objects.requireNonNull(claimId, "claimId");
        return breaches.contains(claimId);
    }

    public void recordWarning(ClaimId claimId) {
        Objects.requireNonNull(claimId, "claimId");
        warnings.add(claimId);
    }

    public void recordBreach(ClaimId claimId) {
        Objects.requireNonNull(claimId, "claimId");
        warnings.add(claimId);
        breaches.add(claimId);
    }
}
