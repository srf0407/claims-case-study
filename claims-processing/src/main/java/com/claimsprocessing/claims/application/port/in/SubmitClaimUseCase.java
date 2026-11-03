package com.claimsprocessing.claims.application.port.in;

import com.claimsprocessing.claims.domain.ClaimId;

public interface SubmitClaimUseCase {

    ClaimId submit(SubmitClaimCommand command);
}
