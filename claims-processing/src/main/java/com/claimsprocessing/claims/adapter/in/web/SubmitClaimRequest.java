package com.claimsprocessing.claims.adapter.in.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.claimsprocessing.claims.domain.ClaimType;

public record SubmitClaimRequest(
        ClaimType claimType,
        String clientId,
        String policyNumber,
        BigDecimal amount,
        LocalDate incidentDate,
        LocalDate dateOfDeath,
        String deathCertificateRef) {
}
