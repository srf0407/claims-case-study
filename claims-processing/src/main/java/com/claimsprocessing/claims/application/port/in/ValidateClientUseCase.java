package com.claimsprocessing.claims.application.port.in;

import com.claimsprocessing.claims.application.ClientCheckResult;

public interface ValidateClientUseCase {

    ClientCheckResult validate(ValidateClientCommand command);
}
