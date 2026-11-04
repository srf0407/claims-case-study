package com.claimsprocessing.claims.adapter.out.policymanager;

import com.claimsprocessing.claims.application.PolicyAssessmentResult;
import com.claimsprocessing.claims.application.port.out.PolicyManagerUnavailableException;

public final class PolicyManagerMapper {

    public PolicyAssessmentResult toResult(PolicyManagerResponse response) {
        if (response == null || response.status() == null) {
            throw unavailable("Policy Manager response is missing a status");
        }
        if ("LAPSED".equals(response.status())) {
            return PolicyAssessmentResult.LAPSED;
        }
        if (!"ACTIVE".equals(response.status())) {
            throw unavailable("Policy Manager returned status " + response.status());
        }
        if (response.covered() == null) {
            throw unavailable("Policy Manager response is missing covered");
        }
        if (!response.covered()) {
            return PolicyAssessmentResult.NO_COVER;
        }
        return PolicyAssessmentResult.COVERED;
    }

    private static PolicyManagerUnavailableException unavailable(String message) {
        return new PolicyManagerUnavailableException(message, new IllegalStateException(message));
    }
}
