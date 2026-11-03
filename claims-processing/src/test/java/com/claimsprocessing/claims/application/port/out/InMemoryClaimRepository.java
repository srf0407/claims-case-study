package com.claimsprocessing.claims.application.port.out;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;

public final class InMemoryClaimRepository implements ClaimRepositoryPort {

    private final Map<ClaimId, Claim> claims = new HashMap<>();

    @Override
    public void save(Claim claim) {
        // Same-id overwrite is a stand-in. The in-memory repository ticket decides it.
        claims.put(claim.id(), claim);
    }

    @Override
    public Optional<Claim> findById(ClaimId id) {
        return Optional.ofNullable(claims.get(id));
    }

    public boolean isEmpty() {
        return claims.isEmpty();
    }
}
