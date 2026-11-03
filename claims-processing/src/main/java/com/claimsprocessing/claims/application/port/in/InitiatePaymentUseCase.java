package com.claimsprocessing.claims.application.port.in;

public interface InitiatePaymentUseCase {

    void initiate(InitiatePaymentCommand command);
}
