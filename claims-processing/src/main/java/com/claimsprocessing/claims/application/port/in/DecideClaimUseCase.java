package com.claimsprocessing.claims.application.port.in;

import com.claimsprocessing.claims.domain.Claim;

public interface DecideClaimUseCase {

    Claim decide(DecideClaimCommand command);
}
