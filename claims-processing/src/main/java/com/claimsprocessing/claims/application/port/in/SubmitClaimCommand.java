package com.claimsprocessing.claims.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.claimsprocessing.claims.domain.ClaimType;

public record SubmitClaimCommand(
        ClaimType claimType,
        String clientId,
        String policyNumber,
        BigDecimal amount,
        LocalDate incidentDate,
        LocalDate dateOfDeath,
        String deathCertificateRef) {
}
