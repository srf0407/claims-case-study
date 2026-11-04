package com.claimsprocessing.claims.adapter.in.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.claimsprocessing.claims.domain.ClaimStatus;
import com.claimsprocessing.claims.domain.ClaimType;
import com.claimsprocessing.claims.domain.RejectionReason;

public record GetClaimResponse(
        UUID claimId,
        String clientId,
        String policyNumber,
        ClaimType claimType,
        BigDecimal amount,
        LocalDate incidentDate,
        LocalDate dateOfDeath,
        String deathCertificateRef,
        ClaimStatus status,
        RejectionReason rejectionReason,
        List<EventResponse> events) {
}
