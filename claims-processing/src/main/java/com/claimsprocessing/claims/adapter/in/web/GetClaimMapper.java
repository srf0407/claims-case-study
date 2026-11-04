package com.claimsprocessing.claims.adapter.in.web;

import java.util.Objects;
import java.util.UUID;

import com.claimsprocessing.claims.application.port.in.GetClaimQuery;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimAccepted;
import com.claimsprocessing.claims.domain.ClaimEvent;
import com.claimsprocessing.claims.domain.ClaimId;
import com.claimsprocessing.claims.domain.ClaimStatusChanged;

public final class GetClaimMapper {

    public GetClaimQuery toQuery(UUID id) {
        Objects.requireNonNull(id, "id");
        return new GetClaimQuery(new ClaimId(id));
    }

    public GetClaimResponse toResponse(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        return new GetClaimResponse(
                claim.id().value(),
                claim.clientId(),
                claim.policyNumber(),
                claim.claimType(),
                claim.amount(),
                claim.incidentDate(),
                claim.dateOfDeath(),
                claim.deathCertificateRef(),
                claim.status(),
                claim.rejectionReason().orElse(null),
                claim.events().stream().map(this::toEvent).toList());
    }

    private EventResponse toEvent(ClaimEvent event) {
        return switch (event) {
            case ClaimAccepted accepted -> new EventResponse(
                    "ACCEPTED", accepted.occurredAt(), null, null, null);
            case ClaimStatusChanged changed -> new EventResponse(
                    "STATUS_CHANGED",
                    changed.occurredAt(),
                    changed.from(),
                    changed.to(),
                    changed.reason().orElse(null));
        };
    }
}
