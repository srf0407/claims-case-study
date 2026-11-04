package com.claimsprocessing.claims.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;

@RestControllerAdvice(assignableTypes = PaymentCallbackController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentCallbackExceptionHandler {

    @ExceptionHandler(PaymentSignatureException.class)
    public ResponseEntity<ProblemDetail> handleSignature(HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.UNAUTHORIZED,
                "/problems/invalid-signature",
                "Invalid signature",
                "Payment signature is invalid");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleConflict(HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.CONFLICT,
                "/problems/payment-conflict",
                "Payment conflict",
                "Claim is not awaiting payment");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(
            IllegalArgumentException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.BAD_REQUEST,
                "/problems/invalid-callback",
                "Invalid callback",
                callbackDetail(exception.getMessage()));
    }

    @ExceptionHandler(JacksonException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.BAD_REQUEST,
                "/problems/invalid-callback",
                "Invalid callback",
                "Request body could not be read");
    }

    private static String callbackDetail(String message) {
        if (message == null) {
            return "Request body could not be read";
        }
        return switch (message) {
            case "claimId" -> "claimId is required";
            case "outcome" -> "outcome is required";
            case "idempotencyKey" -> "idempotencyKey is required";
            case "claimId uuid" -> "claimId must be a UUID";
            case "idempotencyKey mismatch" -> "idempotencyKey does not match the claim";
            default -> "Request body could not be read";
        };
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpServletRequest request, HttpStatus status, String type, String title, String detail) {
        return ProblemDetails.of(type, title, status.value(), detail, request.getRequestURI());
    }
}
