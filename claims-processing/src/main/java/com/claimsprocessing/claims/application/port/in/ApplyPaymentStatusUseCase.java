package com.claimsprocessing.claims.application.port.in;

import com.claimsprocessing.claims.application.PaymentStatusResult;

public interface ApplyPaymentStatusUseCase {

    PaymentStatusResult apply(ApplyPaymentStatusCommand command);
}
