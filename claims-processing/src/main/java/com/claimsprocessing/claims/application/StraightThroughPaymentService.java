package com.claimsprocessing.claims.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;

import com.claimsprocessing.claims.application.port.in.StraightThroughPaymentCommand;
import com.claimsprocessing.claims.application.port.in.StraightThroughPaymentUseCase;
import com.claimsprocessing.claims.application.port.out.ClaimRepositoryPort;
import com.claimsprocessing.claims.application.port.out.PaymentPort;
import com.claimsprocessing.claims.domain.Claim;
import com.claimsprocessing.claims.domain.ClaimStatus;

public final class StraightThroughPaymentService implements StraightThroughPaymentUseCase {

    private final ClaimRepositoryPort claimRepository;
    private final PaymentPort payment;
    private final PaymentRequestMapper paymentRequestMapper;
    private final Clock clock;
    private final BigDecimal limit;

    public StraightThroughPaymentService(
            ClaimRepositoryPort claimRepository,
            PaymentPort payment,
            PaymentRequestMapper paymentRequestMapper,
            Clock clock,
            BigDecimal limit) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.payment = Objects.requireNonNull(payment, "payment");
        this.paymentRequestMapper = Objects.requireNonNull(paymentRequestMapper, "paymentRequestMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.limit = Objects.requireNonNull(limit, "limit");
    }

    @Override
    public StraightThroughResult execute(StraightThroughPaymentCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.claimId(), "claimId");
        Objects.requireNonNull(command.clientCheck(), "clientCheck");
        Objects.requireNonNull(command.policyAssessment(), "policyAssessment");
        Claim claim = claimRepository.findById(command.claimId())
                .orElseThrow(() -> new ClaimNotFoundException(command.claimId()));
        if (claim.status() != ClaimStatus.ACCEPTED) {
            throw new IllegalStateException("Claim " + claim.id().value() + " is " + claim.status().name());
        }
        if (command.clientCheck() != ClientCheckResult.KNOWN) {
            throw new IllegalStateException(
                    "Claim " + claim.id().value() + " client check is " + command.clientCheck().name());
        }
        if (command.policyAssessment() != PolicyAssessmentResult.COVERED) {
            throw new IllegalStateException(
                    "Claim " + claim.id().value() + " policy assessment is " + command.policyAssessment().name());
        }
        if (!claim.isEligibleForStraightThrough(limit, command.flagged())) {
            claimRepository.save(claim.transitionTo(ClaimStatus.AWAITING_ANALYST, clock));
            return StraightThroughResult.SENT_TO_ANALYST;
        }
        payment.initiate(paymentRequestMapper.map(claim));
        claimRepository.save(claim.transitionTo(ClaimStatus.PAYMENT_PENDING, clock));
        return StraightThroughResult.PAYMENT_INITIATED;
    }
}
