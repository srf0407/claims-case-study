package com.claimsprocessing.claims.adapter.out.policymanager;

import java.util.Objects;

import com.claimsprocessing.claims.application.PolicyAssessmentResult;
import com.claimsprocessing.claims.application.port.out.PolicyManagerPort;
import com.claimsprocessing.claims.application.port.out.PolicyManagerUnavailableException;
import com.claimsprocessing.claims.domain.ClaimType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class PolicyManagerClient implements PolicyManagerPort {

    private final RestClient restClient;
    private final PolicyManagerMapper mapper;

    public PolicyManagerClient(RestClient restClient, PolicyManagerMapper mapper) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public PolicyAssessmentResult assess(String policyNumber, ClaimType claimType) {
        try {
            return restClient.get()
                    .uri(builder -> builder
                            .path("/policies/{policyNumber}/cover")
                            .queryParam("claimType", claimType.name())
                            .build(policyNumber))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            return PolicyAssessmentResult.NO_COVER;
                        }
                        if (status == 200) {
                            return mapper.toResult(response.bodyTo(PolicyManagerResponse.class));
                        }
                        throw unavailable("Policy Manager returned HTTP " + status);
                    });
        } catch (PolicyManagerUnavailableException exception) {
            throw exception;
        } catch (RestClientException | HttpMessageConversionException exception) {
            throw unavailable("Policy Manager is unavailable", exception);
        }
    }

    private static PolicyManagerUnavailableException unavailable(String message) {
        return new PolicyManagerUnavailableException(message, new IllegalStateException(message));
    }

    private static PolicyManagerUnavailableException unavailable(String message, Throwable cause) {
        return new PolicyManagerUnavailableException(message, cause);
    }
}
