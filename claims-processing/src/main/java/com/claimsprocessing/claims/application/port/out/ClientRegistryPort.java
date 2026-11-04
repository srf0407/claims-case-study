package com.claimsprocessing.claims.application.port.out;

import com.claimsprocessing.claims.application.ClientCheckResult;
import com.claimsprocessing.claims.domain.ClaimType;

public interface ClientRegistryPort {

    /**
     * {@code ACTIVE} is a known client. {@code DECEASED} is a known client only when
     * {@code claimType} is {@link ClaimType#DEATH}. Any other status is unavailable.
     */
    ClientCheckResult check(String clientId, ClaimType claimType);
}
