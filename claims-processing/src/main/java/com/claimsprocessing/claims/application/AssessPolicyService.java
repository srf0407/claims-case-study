package com.claimsprocessing.claims.application;

import java.time.Clock;
import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.AssessPolicyCommand;
import com.claimsprocessing.claims.application.port.in.AssessPolicyUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.PolicyManagerPort;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.RejectionReason;

public final class AssessPolicyService implements AssessPolicyUseCase {

    private final ClaimRepositoryPort claimRepository;
    private final PolicyManagerPort policyManager;
    private final Clock clock;

    public AssessPolicyService(
            ClaimRepositoryPort claimRepository, PolicyManagerPort policyManager, Clock clock) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.policyManager = Objects.requireNonNull(policyManager, "policyManager");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PolicyAssessmentResult assess(AssessPolicyCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.claimId(), "claimId");
        Claim claim = claimRepository.findById(command.claimId())
                .orElseThrow(() -> new ClaimNotFoundException(command.claimId()));
        if (claim.status() != ClaimStatus.ACCEPTED) {
            throw new IllegalStateException("Claim " + claim.id().value() + " is " + claim.status().name());
        }
        PolicyAssessmentResult result = policyManager.assess(claim.policyNumber(), claim.claimType());
        if (result == PolicyAssessmentResult.LAPSED) {
            claimRepository.save(claim.reject(RejectionReason.POLICY_LAPSED, clock));
        } else if (result == PolicyAssessmentResult.NO_COVER) {
            claimRepository.save(claim.reject(RejectionReason.NO_COVER, clock));
        }
        return result;
    }
}
