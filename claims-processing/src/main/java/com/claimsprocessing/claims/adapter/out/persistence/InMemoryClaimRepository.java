package com.claimsprocessing.claims.adapter.out.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;

public final class InMemoryClaimRepository implements ClaimRepositoryPort {

    private final ConcurrentHashMap<ClaimId, Claim> claims = new ConcurrentHashMap<>();

    @Override
    public void save(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        claims.put(claim.id(), claim);
    }

    @Override
    public Optional<Claim> findById(ClaimId id) {
        // HashMap-based implementations return empty for a null id.
        // ConcurrentHashMap.get(null) would throw.
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(claims.get(id));
    }
}
