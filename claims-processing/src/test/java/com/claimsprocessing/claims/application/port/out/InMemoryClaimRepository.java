package com.claimsprocessing.claims.application.port.out;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimType;

public final class InMemoryClaimRepository implements ClaimRepositoryPort {

    private final Map<ClaimId, Claim> claims = new LinkedHashMap<>();

    @Override
    public void save(Claim claim) {
        // Same-id overwrite is a stand-in. The in-memory repository ticket decides it.
        claims.put(claim.id(), claim);
    }

    @Override
    public Optional<Claim> findById(ClaimId id) {
        return Optional.ofNullable(claims.get(id));
    }

    @Override
    public List<Claim> findOpenDeathClaims() {
        return claims.values().stream()
                .filter(claim -> claim.claimType() == ClaimType.DEATH)
                .filter(claim -> claim.status() == ClaimStatus.ACCEPTED
                        || claim.status() == ClaimStatus.AWAITING_ANALYST)
                .toList();
    }

    public boolean isEmpty() {
        return claims.isEmpty();
    }

    public List<Claim> savedClaims() {
        return List.copyOf(claims.values());
    }
}
