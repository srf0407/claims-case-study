package com.claimsprocessing.claims.application;

/**
 * The Policy Manager's decision for one claim.
 * When a policy is both lapsed and without cover for the claim type, the result is {@link #LAPSED}.
 */
public enum PolicyAssessmentResult {
    COVERED,
    LAPSED,
    NO_COVER
}
