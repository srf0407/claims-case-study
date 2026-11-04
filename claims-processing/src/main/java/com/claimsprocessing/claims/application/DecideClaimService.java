package com.claimsprocessing.claims.application;

import java.time.Clock;
import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.ClaimDecision;
import com.claimsprocessing.claims.application.port.in.DecideClaimCommand;
import com.claimsprocessing.claims.application.port.in.DecideClaimUseCase;
import com.claimsprocessing.claims.application.port.in.InitiatePaymentCommand;
import com.claimsprocessing.claims.application.port.in.InitiatePaymentUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.RejectionReason;

public final class DecideClaimService implements DecideClaimUseCase {

    private final ClaimRepositoryPort claimRepository;
    private final InitiatePaymentUseCase initiatePayment;
    private final Clock clock;

    public DecideClaimService(
            ClaimRepositoryPort claimRepository, InitiatePaymentUseCase initiatePayment, Clock clock) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.initiatePayment = Objects.requireNonNull(initiatePayment, "initiatePayment");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Claim decide(DecideClaimCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.claimId(), "claimId");
        Objects.requireNonNull(command.decision(), "decision");
        Claim claim = claimRepository.findById(command.claimId())
                .orElseThrow(() -> new ClaimNotFoundException(command.claimId()));
        if (claim.status() != ClaimStatus.AWAITING_ANALYST) {
            throw new ClaimNotAwaitingAnalystException();
        }
        if (command.decision() == ClaimDecision.APPROVE) {
            Claim approved = claim.transitionTo(ClaimStatus.APPROVED, clock);
            if (!claimRepository.replaceIfStatus(approved, ClaimStatus.AWAITING_ANALYST)) {
                throw new ClaimNotAwaitingAnalystException();
            }
            initiatePayment.initiate(new InitiatePaymentCommand(approved.id()));
            return claimRepository.findById(approved.id())
                    .orElseThrow(() -> new ClaimNotFoundException(approved.id()));
        }
        Claim rejected = claim.reject(RejectionReason.ANALYST_REJECTED, clock);
        if (!claimRepository.replaceIfStatus(rejected, ClaimStatus.AWAITING_ANALYST)) {
            throw new ClaimNotAwaitingAnalystException();
        }
        return rejected;
    }
}
