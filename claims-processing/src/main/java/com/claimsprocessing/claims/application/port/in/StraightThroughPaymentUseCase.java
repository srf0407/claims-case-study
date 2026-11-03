package com.claimsprocessing.claims.application.port.in;

import com.claimsprocessing.claims.application.StraightThroughResult;

public interface StraightThroughPaymentUseCase {

    StraightThroughResult execute(StraightThroughPaymentCommand command);
}
