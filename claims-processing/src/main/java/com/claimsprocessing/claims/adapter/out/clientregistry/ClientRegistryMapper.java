package com.claimsprocessing.claims.adapter.out.clientregistry;

import com.claimsprocessing.claims.application.ClientCheckResult;
import com.claimsprocessing.claims.application.port.out.ClientRegistryUnavailableException;

public final class ClientRegistryMapper {

    public ClientCheckResult toResult(ClientRegistryResponse response) {
        if (response != null && "ACTIVE".equals(response.status())) {
            return ClientCheckResult.KNOWN;
        }
        String status = response == null ? null : response.status();
        throw new ClientRegistryUnavailableException(
                "Client Registry returned status " + status,
                new IllegalStateException("status=" + status));
    }
}
