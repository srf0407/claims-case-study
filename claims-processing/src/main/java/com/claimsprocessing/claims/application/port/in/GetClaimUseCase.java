package com.claimsprocessing.claims.application.port.in;

import com.claimsprocessing.claims.domain.Claim;

public interface GetClaimUseCase {

    Claim get(GetClaimQuery query);
}
