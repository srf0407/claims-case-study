package com.claimsprocessing.claims.adapter.in.web;

import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.SubmitClaimCommand;
import com.claimsprocessing.claims.domain.ClaimId;

public final class SubmitClaimMapper {

    public SubmitClaimCommand toCommand(SubmitClaimRequest request) {
        Objects.requireNonNull(request, "request");
        return new SubmitClaimCommand(
                request.claimType(),
                request.clientId(),
                request.policyNumber(),
                request.amount(),
                request.incidentDate(),
                request.dateOfDeath(),
                request.deathCertificateRef());
    }

    public SubmitClaimResponse toResponse(ClaimId claimId) {
        Objects.requireNonNull(claimId, "claimId");
        return new SubmitClaimResponse(claimId.value());
    }
}
