package com.claimsprocessing.claims.application.port.out;

import com.claimsprocessing.claims.application.ClientCheckResult;

public interface ClientRegistryPort {

    ClientCheckResult check(String clientId);
}
