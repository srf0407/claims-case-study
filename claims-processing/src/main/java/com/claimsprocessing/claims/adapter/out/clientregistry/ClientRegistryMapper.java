package com.claimsprocessing.claims.adapter.out.clientregistry;

import java.util.Objects;

import com.claimsprocessing.claims.application.ClientCheckResult;
import com.claimsprocessing.claims.application.port.out.ClientRegistryUnavailableException;
import com.claimsprocessing.claims.domain.ClaimType;

public final class ClientRegistryMapper {

    public ClientCheckResult toResult(ClientRegistryResponse response, ClaimType claimType) {
        Objects.requireNonNull(claimType, "claimType");
        if (response != null && "ACTIVE".equals(response.status())) {
            return ClientCheckResult.KNOWN;
        }
        if (response != null && claimType == ClaimType.DEATH && "DECEASED".equals(response.status())) {
            return ClientCheckResult.KNOWN;
        }
        String status = response == null ? null : response.status();
        throw new ClientRegistryUnavailableException(
                "Client Registry returned status " + status,
                new IllegalStateException("status=" + status));
    }
}
