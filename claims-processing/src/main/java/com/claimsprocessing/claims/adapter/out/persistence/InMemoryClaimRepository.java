package com.claimsprocessing.claims.adapter.out.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimType;

public final class InMemoryClaimRepository implements ClaimRepositoryPort {

    private final ConcurrentHashMap<ClaimId, Claim> claims = new ConcurrentHashMap<>();

    @Override
    public void save(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        claims.put(claim.id(), claim);
    }

    @Override
    public boolean replaceIfStatus(Claim claim, ClaimStatus expected) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(expected, "expected");
        boolean[] replaced = {false};
        claims.compute(claim.id(), (id, current) -> {
            if (current != null && current.status() == expected) {
                replaced[0] = true;
                return claim;
            }
            return current;
        });
        return replaced[0];
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

    @Override
    public List<Claim> findOpenDeathClaims() {
        return claims.values().stream()
                .filter(claim -> claim.claimType() == ClaimType.DEATH)
                .filter(claim -> claim.status() == ClaimStatus.ACCEPTED
                        || claim.status() == ClaimStatus.AWAITING_ANALYST)
                .toList();
    }
}
