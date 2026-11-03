package com.claimsprocessing.claims.application.port.in;

import com.claimsprocessing.claims.application.ClientCheckResult;
import com.claimsprocessing.claims.application.PolicyAssessmentResult;
import com.claimsprocessing.claims.domain.ClaimId;

public record StraightThroughPaymentCommand(
        ClaimId claimId,
        ClientCheckResult clientCheck,
        PolicyAssessmentResult policyAssessment,
        boolean flagged) {
}
