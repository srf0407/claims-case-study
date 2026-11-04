package com.claimsprocessing.claims.adapter.out.persistence;

import java.util.List;

public record ClaimItem(
        String id,
        String clientId,
        String policyNumber,
        String claimType,
        String amount,
        String incidentDate,
        String dateOfDeath,
        String deathCertificateRef,
        String status,
        String rejectionReason,
        List<ClaimEventItem> events) {
}
