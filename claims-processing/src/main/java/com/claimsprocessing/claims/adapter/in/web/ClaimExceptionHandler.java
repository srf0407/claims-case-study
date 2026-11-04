package com.claimsprocessing.claims.adapter.in.web;

import com.claimsprocessing.claims.application.ClaimNotFoundException;
import com.claimsprocessing.claims.domain.InvalidClaimException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ClaimExceptionHandler {

    @ExceptionHandler(InvalidClaimException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidClaim() {
    }

    @ExceptionHandler(ClaimNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleClaimNotFound() {
    }
}
