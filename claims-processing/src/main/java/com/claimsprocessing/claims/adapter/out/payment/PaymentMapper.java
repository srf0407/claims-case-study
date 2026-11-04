package com.claimsprocessing.claims.adapter.out.payment;

import com.claimsprocessing.claims.application.port.out.PaymentRequest;

public final class PaymentMapper {

    public PaymentInitiateRequest toRequest(PaymentRequest request) {
        return new PaymentInitiateRequest(
                request.claimId().value().toString(),
                request.clientId(),
                request.policyNumber(),
                request.amount());
    }
}
