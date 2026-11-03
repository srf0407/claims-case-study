package com.claimsprocessing.claims.application.port.out;

import java.util.Optional;

import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;

public interface ClaimRepositoryPort {

    void save(Claim claim);

    Optional<Claim> findById(ClaimId id);
}
