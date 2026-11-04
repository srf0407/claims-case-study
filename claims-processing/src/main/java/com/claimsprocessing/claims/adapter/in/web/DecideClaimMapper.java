package com.claimsprocessing.claims.adapter.in.web;

import java.util.Objects;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.in.ClaimDecision;
import com.claimsprocessing.claims.application.port.in.DecideClaimCommand;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimId;

public final class DecideClaimMapper {

    public DecideClaimCommand toCommand(UUID id, DecideClaimRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.decision() == null) {
            throw new UnreadableDecisionException();
        }
        return new DecideClaimCommand(new ClaimId(id), toDecision(request.decision()));
    }

    public DecideClaimResponse toResponse(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        return new DecideClaimResponse(claim.id().value(), claim.status());
    }

    private static ClaimDecision toDecision(DecideClaimRequest.Decision decision) {
        return switch (decision) {
            case APPROVE -> ClaimDecision.APPROVE;
            case REJECT -> ClaimDecision.REJECT;
        };
    }
}
