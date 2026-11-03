package com.claimsprocessing.claims.application.port.out;

import com.claimsprocessing.claims.application.PolicyAssessmentResult;
import com.claimsprocessing.claims.domain.ClaimType;

public interface PolicyManagerPort {

    PolicyAssessmentResult assess(String policyNumber, ClaimType claimType);
}
