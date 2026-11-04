package com.claimsprocessing.claims.application;

import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.GetClaimQuery;
import com.claimsprocessing.claims.application.port.in.GetClaimUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.domain.Claim;

public final class GetClaimService implements GetClaimUseCase {

    private final ClaimRepositoryPort claimRepository;

    public GetClaimService(ClaimRepositoryPort claimRepository) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
    }

    @Override
    public Claim get(GetClaimQuery query) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(query.claimId(), "claimId");
        return claimRepository.findById(query.claimId())
                .orElseThrow(() -> new ClaimNotFoundException(query.claimId()));
    }
}
