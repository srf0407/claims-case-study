package com.claimsprocessing.claims.application;

import java.time.Clock;
import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.ValidateClientCommand;
import com.claimsprocessing.claims.application.port.in.ValidateClientUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.ClientRegistryPort;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.RejectionReason;

public final class ValidateClientService implements ValidateClientUseCase {

    private final ClaimRepositoryPort claimRepository;
    private final ClientRegistryPort clientRegistry;
    private final Clock clock;

    public ValidateClientService(
            ClaimRepositoryPort claimRepository, ClientRegistryPort clientRegistry, Clock clock) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.clientRegistry = Objects.requireNonNull(clientRegistry, "clientRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ClientCheckResult validate(ValidateClientCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.claimId(), "claimId");
        Claim claim = claimRepository.findById(command.claimId())
                .orElseThrow(() -> new ClaimNotFoundException(command.claimId()));
        if (claim.status() != ClaimStatus.ACCEPTED) {
            throw new IllegalStateException("Claim " + claim.id().value() + " is " + claim.status().name());
        }
        ClientCheckResult result = clientRegistry.check(claim.clientId());
        if (result == ClientCheckResult.UNKNOWN) {
            claimRepository.save(claim.reject(RejectionReason.UNKNOWN_CLIENT, clock));
        }
        return result;
    }
}
