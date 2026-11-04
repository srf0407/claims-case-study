package com.claimsprocessing.claims.adapter.in.web;

import com.claimsprocessing.claims.application.PaymentStatusResult;

public record PaymentCallbackResponse(PaymentStatusResult result) {
}
