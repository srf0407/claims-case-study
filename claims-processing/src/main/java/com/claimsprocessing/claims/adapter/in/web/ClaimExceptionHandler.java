package com.claimsprocessing.claims.adapter.in.web;

import com.claimsprocessing.claims.application.ClaimNotAwaitingAnalystException;
import com.claimsprocessing.claims.application.ClaimNotFoundException;
import com.claimsprocessing.claims.domain.InvalidClaimException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ClaimExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ClaimExceptionHandler.class);

    @ExceptionHandler(InvalidClaimException.class)
    public ResponseEntity<ProblemDetail> handleInvalidClaim(
            InvalidClaimException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.BAD_REQUEST, "/problems/invalid-claim", "Invalid claim", exception.getMessage());
    }

    @ExceptionHandler(ClaimNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleClaimNotFound(HttpServletRequest request) {
        return problem(
                request, HttpStatus.NOT_FOUND, "/problems/claim-not-found", "Claim not found", "Claim not found");
    }

    @ExceptionHandler(ClaimNotAwaitingAnalystException.class)
    public ResponseEntity<ProblemDetail> handleNotAwaitingAnalyst(HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.CONFLICT,
                "/problems/claim-not-awaiting-analyst",
                "Claim is not awaiting an analyst",
                "Claim is not awaiting an analyst");
    }

    @ExceptionHandler(UnreadableDecisionException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableDecision(HttpServletRequest request) {
        return malformed(request, "Request body could not be read");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpServletRequest request) {
        return malformed(request, "Request body could not be read");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(HttpServletRequest request) {
        return malformed(request, "id must be a UUID");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        // ErrorResponse is not a Throwable, so it cannot be an @ExceptionHandler type.
        // Spring MVC client errors implement it and would otherwise become a 500.
        if (exception instanceof ErrorResponse errorResponse) {
            return handleRequestError(errorResponse, request);
        }
        log.error("Unexpected failure", exception);
        return problem(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "/problems/internal-error",
                "Internal error",
                "An unexpected error occurred");
    }

    private static ResponseEntity<ProblemDetail> handleRequestError(
            ErrorResponse error, HttpServletRequest request) {
        HttpStatusCode statusCode = error.getStatusCode();
        return ProblemDetails.of(
                "/problems/request-error",
                reasonPhrase(statusCode),
                statusCode.value(),
                "Request could not be handled",
                request.getRequestURI());
    }

    private static String reasonPhrase(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            return Integer.toString(statusCode.value());
        }
        return status.getReasonPhrase();
    }

    private static ResponseEntity<ProblemDetail> malformed(HttpServletRequest request, String detail) {
        return problem(request, HttpStatus.BAD_REQUEST, "/problems/malformed-request", "Malformed request", detail);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpServletRequest request, HttpStatus status, String type, String title, String detail) {
        return ProblemDetails.of(type, title, status.value(), detail, request.getRequestURI());
    }
}
