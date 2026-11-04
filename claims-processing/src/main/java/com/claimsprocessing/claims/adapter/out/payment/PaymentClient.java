package com.claimsprocessing.claims.adapter.out.payment;

import java.util.Objects;

import com.claimsprocessing.claims.application.port.out.PaymentPort;
import com.claimsprocessing.claims.application.port.out.PaymentRequest;
import com.claimsprocessing.claims.application.port.out.PaymentUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class PaymentClient implements PaymentPort {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;
    private final PaymentMapper mapper;

    public PaymentClient(RestClient restClient, PaymentMapper mapper) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void initiate(PaymentRequest request) {
        try {
            PaymentInitiateRequest body = mapper.toRequest(request);
            restClient.post()
                    .uri("/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(IDEMPOTENCY_KEY_HEADER, request.idempotencyKey())
                    .body(body)
                    .exchange((httpRequest, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 202 || status == 409) {
                            return null;
                        }
                        throw unavailable("Payment System returned HTTP " + status);
                    });
        } catch (PaymentUnavailableException exception) {
            throw exception;
        } catch (RestClientException | HttpMessageConversionException exception) {
            throw unavailable("Payment System is unavailable", exception);
        }
    }

    private static PaymentUnavailableException unavailable(String message) {
        return new PaymentUnavailableException(message, new IllegalStateException(message));
    }

    private static PaymentUnavailableException unavailable(String message, Throwable cause) {
        return new PaymentUnavailableException(message, cause);
    }
}
