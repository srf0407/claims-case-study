package com.claimsprocessing.claims.application.port.out;

public interface PaymentPort {

    void initiate(PaymentRequest request);
}
