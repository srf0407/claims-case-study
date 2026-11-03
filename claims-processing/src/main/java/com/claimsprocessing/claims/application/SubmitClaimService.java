package com.claimsprocessing.claims.application;

import java.time.Clock;
import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.SubmitClaimCommand;
import com.claimsprocessing.claims.application.port.in.SubmitClaimUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;

public final class SubmitClaimService implements SubmitClaimUseCase {

    private final ClaimRepositoryPort claimRepository;
    private final Clock clock;

    public SubmitClaimService(ClaimRepositoryPort claimRepository, Clock clock) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ClaimId submit(SubmitClaimCommand command) {
        Objects.requireNonNull(command, "command");
        Claim claim = Claim.accept(
                command.claimType(),
                command.clientId(),
                command.policyNumber(),
                command.amount(),
                command.incidentDate(),
                command.dateOfDeath(),
                command.deathCertificateRef(),
                clock);
        claimRepository.save(claim);
        return claim.id();
    }
}
