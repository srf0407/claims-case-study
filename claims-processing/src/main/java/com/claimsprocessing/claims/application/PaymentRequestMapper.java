package com.claimsprocessing.claims.application;

import java.util.Objects;

import com.claimsprocessing.claims.application.port.out.PaymentRequest;
import com.claimsprocessing.claims.domain.Claim;

public final class PaymentRequestMapper {

    public PaymentRequest map(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        return new PaymentRequest(
                claim.id().value().toString(),
                claim.id(),
                claim.clientId(),
                claim.policyNumber(),
                claim.amount());
    }
}
