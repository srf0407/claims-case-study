package com.claimsprocessing.claims.adapter.in.web;

import java.io.IOException;
import java.util.Objects;

import com.claimsprocessing.claims.application.PaymentStatusResult;
import com.claimsprocessing.claims.application.port.in.ApplyPaymentStatusUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentCallbackController {

    private final PaymentSignatureVerifier verifier;
    private final PaymentCallbackMapper mapper;
    private final ApplyPaymentStatusUseCase applyPaymentStatus;

    public PaymentCallbackController(
            PaymentSignatureVerifier verifier,
            PaymentCallbackMapper mapper,
            ApplyPaymentStatusUseCase applyPaymentStatus) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.applyPaymentStatus = Objects.requireNonNull(applyPaymentStatus, "applyPaymentStatus");
    }

    @PostMapping("/payments/callback")
    public PaymentCallbackResponse callback(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        verifier.verify(body, request.getHeader(PaymentSignatureVerifier.SIGNATURE_HEADER));
        PaymentStatusResult result = applyPaymentStatus.apply(mapper.toCommand(mapper.read(body)));
        return mapper.toResponse(result);
    }
}
