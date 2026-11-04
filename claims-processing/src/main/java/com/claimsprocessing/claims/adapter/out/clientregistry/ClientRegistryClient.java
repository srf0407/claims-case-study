package com.claimsprocessing.claims.adapter.out.clientregistry;

import java.util.Objects;

import com.claimsprocessing.claims.application.ClientCheckResult;
import com.claimsprocessing.claims.application.port.out.ClientRegistryPort;
import com.claimsprocessing.claims.application.port.out.ClientRegistryUnavailableException;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class ClientRegistryClient implements ClientRegistryPort {

    private final RestClient restClient;
    private final ClientRegistryMapper mapper;

    public ClientRegistryClient(RestClient restClient, ClientRegistryMapper mapper) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public ClientCheckResult check(String clientId) {
        try {
            return restClient.get()
                    .uri("/clients/{clientId}", clientId)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            return ClientCheckResult.UNKNOWN;
                        }
                        if (status == 200) {
                            return mapper.toResult(response.bodyTo(ClientRegistryResponse.class));
                        }
                        throw unavailable("Client Registry returned HTTP " + status);
                    });
        } catch (ClientRegistryUnavailableException exception) {
            throw exception;
        } catch (RestClientException | HttpMessageConversionException exception) {
            throw unavailable("Client Registry is unavailable", exception);
        }
    }

    private static ClientRegistryUnavailableException unavailable(String message) {
        return new ClientRegistryUnavailableException(message, new IllegalStateException(message));
    }

    private static ClientRegistryUnavailableException unavailable(String message, Throwable cause) {
        return new ClientRegistryUnavailableException(message, cause);
    }
}
