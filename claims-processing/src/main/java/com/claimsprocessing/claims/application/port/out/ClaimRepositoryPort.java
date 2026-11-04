package com.claimsprocessing.claims.application.port.out;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatus;

public interface ClaimRepositoryPort {

    void save(Claim claim);

    /**
     * Stores {@code claim} when the stored claim still has {@code expected} status.
     * Returns false when that status has already changed.
     * A test fake may overwrite and return true. A persistence adapter checks the status.
     */
    default boolean replaceIfStatus(Claim claim, ClaimStatus expected) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(expected, "expected");
        save(claim);
        return true;
    }

    Optional<Claim> findById(ClaimId id);

    /**
     * Death claims in {@code ACCEPTED} or {@code AWAITING_ANALYST}.
     */
    List<Claim> findOpenDeathClaims();
}
