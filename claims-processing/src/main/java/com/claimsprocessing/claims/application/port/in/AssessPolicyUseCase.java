package com.claimsprocessing.claims.application.port.in;

import com.claimsprocessing.claims.application.PolicyAssessmentResult;

public interface AssessPolicyUseCase {

    PolicyAssessmentResult assess(AssessPolicyCommand command);
}
