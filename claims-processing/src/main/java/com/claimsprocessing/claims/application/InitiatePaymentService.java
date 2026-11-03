package com.claimsprocessing.claims.application;

import java.time.Clock;
import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.InitiatePaymentCommand;
import com.claimsprocessing.claims.application.port.in.InitiatePaymentUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.PaymentPort;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimStatus;

public final class InitiatePaymentService implements InitiatePaymentUseCase {

    private final ClaimRepositoryPort claimRepository;
    private final PaymentPort payment;
    private final PaymentRequestMapper paymentRequestMapper;
    private final Clock clock;

    public InitiatePaymentService(
            ClaimRepositoryPort claimRepository,
            PaymentPort payment,
            PaymentRequestMapper paymentRequestMapper,
            Clock clock) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.payment = Objects.requireNonNull(payment, "payment");
        this.paymentRequestMapper = Objects.requireNonNull(paymentRequestMapper, "paymentRequestMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void initiate(InitiatePaymentCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.claimId(), "claimId");
        Claim claim = claimRepository.findById(command.claimId())
                .orElseThrow(() -> new ClaimNotFoundException(command.claimId()));
        if (claim.status() != ClaimStatus.APPROVED) {
            throw new IllegalStateException("Claim " + claim.id().value() + " is " + claim.status().name());
        }
        payment.initiate(paymentRequestMapper.map(claim));
        claimRepository.save(claim.transitionTo(ClaimStatus.PAYMENT_PENDING, clock));
    }
}
